package com.voiceassistant.service;

import com.voiceassistant.dto.TextAnalysisJobResponseDTO;
import com.voiceassistant.dto.TextAnalysisJobStatus;
import com.voiceassistant.dto.TextAnalysisRequestDTO;
import com.voiceassistant.dto.TextAnalysisResponseDTO;
import com.voiceassistant.exception.TextAnalysisException;
import com.voiceassistant.model.AppUser;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TextAnalysisJobService {

    private static final Duration JOB_TTL = Duration.ofHours(2);
    private static final int MAX_FINISHED_JOBS = 50;

    private final TextAnalysisService textAnalysisService;
    private final AppUserService appUserService;
    private final ThreadPoolExecutor executor;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();

    public TextAnalysisJobService(TextAnalysisService textAnalysisService, AppUserService appUserService) {
        this.textAnalysisService = textAnalysisService;
        this.appUserService = appUserService;
        this.executor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(5),
                daemonThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    public TextAnalysisJobResponseDTO startJob(TextAnalysisRequestDTO request) {
        cleanupFinishedJobs();
        AppUser owner = appUserService.getCurrentUser();
        String jobId = UUID.randomUUID().toString();
        Job job = new Job(jobId, owner.getId());
        jobs.put(jobId, job);

        try {
            executor.execute(() -> runJob(job, request));
        } catch (RejectedExecutionException e) {
            jobs.remove(jobId);
            throw new TextAnalysisException("Too many text analyses are queued. Please try again shortly.", e);
        }

        return toResponse(job);
    }

    public TextAnalysisJobResponseDTO getJob(String jobId) {
        cleanupFinishedJobs();
        AppUser owner = appUserService.getCurrentUser();
        Job job = jobs.get(jobId);
        if (job == null) {
            throw new NoSuchElementException("Text analysis job not found");
        }
        if (!Objects.equals(job.ownerId, owner.getId())) {
            throw new AccessDeniedException("Text analysis job belongs to another user");
        }
        return toResponse(job);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private void runJob(Job job, TextAnalysisRequestDTO request) {
        job.status = TextAnalysisJobStatus.RUNNING;
        job.updatedAt = Instant.now();
        log.info("Started text analysis job jobId={} ownerId={}", job.jobId, job.ownerId);
        try {
            TextAnalysisResponseDTO result = textAnalysisService.analyze(request);
            job.result = result;
            job.status = TextAnalysisJobStatus.SUCCEEDED;
            job.message = null;
            log.info("Completed text analysis job jobId={} ownerId={}", job.jobId, job.ownerId);
        } catch (RuntimeException e) {
            job.status = TextAnalysisJobStatus.FAILED;
            job.message = safeErrorMessage(e);
            log.warn("Failed text analysis job jobId={} ownerId={} error={}", job.jobId, job.ownerId, e.toString());
        } finally {
            job.updatedAt = Instant.now();
        }
    }

    private TextAnalysisJobResponseDTO toResponse(Job job) {
        return new TextAnalysisJobResponseDTO(job.jobId, job.status, job.result, job.message);
    }

    private String safeErrorMessage(RuntimeException e) {
        if (e.getMessage() == null || e.getMessage().isBlank()) {
            return "Text analysis failed";
        }
        return e.getMessage();
    }

    private void cleanupFinishedJobs() {
        Instant cutoff = Instant.now().minus(JOB_TTL);
        jobs.values().removeIf(job -> isFinished(job.status) && job.updatedAt.isBefore(cutoff));

        long finishedCount = jobs.values().stream()
                .filter(job -> isFinished(job.status))
                .count();
        if (finishedCount <= MAX_FINISHED_JOBS) {
            return;
        }

        jobs.values().stream()
                .filter(job -> isFinished(job.status))
                .sorted(Comparator.comparing(job -> job.updatedAt))
                .limit(finishedCount - MAX_FINISHED_JOBS)
                .map(job -> job.jobId)
                .toList()
                .forEach(jobs::remove);
    }

    private boolean isFinished(TextAnalysisJobStatus status) {
        return status == TextAnalysisJobStatus.SUCCEEDED || status == TextAnalysisJobStatus.FAILED;
    }

    private ThreadFactory daemonThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "text-analysis-worker");
            thread.setDaemon(true);
            return thread;
        };
    }

    private static final class Job {
        private final String jobId;
        private final Long ownerId;
        private final Instant createdAt;
        private volatile Instant updatedAt;
        private volatile TextAnalysisJobStatus status;
        private volatile TextAnalysisResponseDTO result;
        private volatile String message;

        private Job(String jobId, Long ownerId) {
            this.jobId = jobId;
            this.ownerId = ownerId;
            this.createdAt = Instant.now();
            this.updatedAt = this.createdAt;
            this.status = TextAnalysisJobStatus.PENDING;
        }
    }
}

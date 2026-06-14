package com.voiceassistant.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TodoItemSchemaMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public TodoItemSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("alter table todo_item alter column description type text");
    }
}

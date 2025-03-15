#!/bin/zsh

# Ensure we're in the project root directory
cd "$(dirname "$0")"

# Verify the pom.xml file exists
if [ ! -f "pom.xml" ]; then
  echo "Error: pom.xml not found in the current directory"
  echo "Current directory: $(pwd)"
  echo "Contents of directory:"
  ls -la
  exit 1
fi

# Build the Docker image
podman build -t azizzakiryarov/voice-assistant:latest .

# Output success message if build succeeds
if [ $? -eq 0 ]; then
  echo "Docker image built successfully: azizzakiryarov/voice-assistant:latest"
fi

# Push the Docker image to Docker Hub
podman push azizzakiryarov/voice-assistant:latest
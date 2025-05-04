# Use an official Python runtime as a parent image
FROM python:3.12-slim

# Set environment variables
ENV PYTHONUNBUFFERED=1

# --- Install build dependencies
# Update apt package list and install C++ compiler and essential build tools
# Required by packages like chroma-hnswlib that compile C++ extensions
RUN apt-get update && \
    apt-get install -y --no-install-recommends build-essential && \
    rm -rf /var/lib/apt/lists/*
# -------------------------------------------------------

# Set the working directory in the container
WORKDIR /app

# Copy the requirements file into the container
COPY requirements.txt requirements.txt

# Install Python dependencies (using the fully pinned requirements.txt)
RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir -r requirements.txt

RUN pip install --no-cache-dir setuptools

# Copy the rest of the application code
COPY . .

# Inform Docker that the container listens on port 5001
EXPOSE 5001

# Define the command to run your app
CMD ["python", "app.py"]
# MONI - AI-Powered IELTS Learning Platform (Backend)

MONI is a monolithic backend system built with **Java 17** and **Spring Boot 3.5** designed to automate and optimize IELTS preparation. The platform supports real-time Speaking examinations via WebSockets, automated Writing evaluation using Large Language Models (LLMs), and an adaptive learning roadmap generation based on student performance.

---

## 🚀 Authentication & Quick Start Guide

> ⚠️ **IMPORTANT:** To see the available practice tests, evaluate essays, or access the dashboard, you **must be authenticated**. Unauthenticated requests to core APIs will be blocked by Spring Security.

### Option 1: Quick Social Login (Recommended for Reviewers)
1. Navigate to our live frontend: [moni-fe.vercel.app](https://moni-fe.vercel.app)
2. Click on **Login / Sign In** and select **Continue with Google**.
3. Once authenticated via OAuth2, your session will be fully initialized, and all test modules (Speaking, Writing, Reading, Listening) will become visible and accessible.

### Option 2: Local API Authentication (via Postman)
If you are testing the REST endpoints locally:
1. Trigger the Google OAuth2 flow or use the register/login endpoints (if local mock auth is enabled).
2. Grab the `accessToken` (JWT) from the response payload.
3. In Postman, go to the **Authorization** tab, select **Bearer Token**, and paste your token.

---

## ✨ Key Features

### 1. Real-time Speaking Exam (WebSocket-driven)
* Handles continuous raw audio byte streams via asynchronous handlers without blocking the server threads.
* Integrates **NVIDIA Whisper V3 API** for accurate Speech-to-Text conversion.
* Leverages **Spring AI** (Google Gemini & LLaMA 4 Maverick via NVIDIA) to grade responses based on the 4 official IELTS criteria: *Fluency, Grammar, Vocabulary, and Pronunciation*.

### 2. Writing Evaluation Module (REST API)
* Accepts essay submissions (IELTS Academic Task 1 & Task 2).
* Evaluates texts against official rubrics using cached prompt templates to ensure fast, structured JSON responses.
* Calculates precise band scores implementing official IELTS rounding and penalty rules.

### 3. Smart Adaptive Roadmap & Progress Tracking
* Analyzes initial placement tests to generate customized study plans.
* Recommends specific targeted exercises rather than random tasks to improve weak areas.

### 4. Enterprise-Grade Architecture
* **HikariCP Connection Pooling:** Configured with robust leak detection and optimal recycling parameters to support concurrent testing sessions.
* **Sliding Window Rate Limiting:** Implemented via **Caffeine Cache** to protect third-party AI and authentication endpoints from brute-force or high-token-cost exploits.

---

## 🛠️ Technology Stack

* **Core Framework:** Java 17, Spring Boot 3.5, Spring Security (OAuth2 Resource Server / JWT)
* **Database & ORM:** PostgreSQL, Hibernate, Spring Data JPA
* **AI Ecosystem:** Spring AI (OpenAI/Gemini integrations), NVIDIA API Catalog
* **Caching & Limits:** Caffeine Cache
* **Formatting & Quality:** Spotless (auto-formatting Checkstyle), MapStruct (DTO Mapping)
* **Containerization:** Docker, Docker Compose

---

## 📦 Local Installation & Setup

### Prerequisites
* Java 17 or higher
* Maven 3.x
* PostgreSQL instance
* Valid API keys configured in environment variables (`.env`)

### Step-by-Step Execution

1. **Clone the repository:**
   ```bash
   git clone [https://github.com/hngvu/ielts-system-backend.git](https://github.com/hngvu/ielts-system-backend.git)
   cd ielts-system-backend
   ```

2. **Configure Environment Variables:**
   Create a `.env` file in the root directory and add the following template keys (replace with your actual keys):
   ```properties
   DB_URL=jdbc:postgresql://localhost:5432/moni
   UB_USERNAME=your_db_username
   DB_PASSWORD=your_db_password
   NVIDIA_API_KEY=your_nvidia_key
   GEMINI_API_KEY=your_gemini_key
   SEPAY_API_KEY=your_sepay_key
   ASSEMBLYAI_API_KEY=your_assemblyai_key
   ELEVENLABS_API_KEY=your_elevenlabs_key
   DAILY_API_KEY=your_daily_key
   ```

3. **Format and Build the Code:**
   ```bash
   mvn spotless:apply
   mvn clean package
   ```

4. **Run the Application:**
   ```bash
   mvn spring-boot:run
   ```
   The server will spin up locally at `http://localhost:8080`. You can review the interactive API contracts via Swagger UI at `http://localhost:8080/swagger-ui.html`.

---

## 👥 Contributors
This project was developed by a team of software engineering students at FPT University.
* **Nguyễn Phúc Tấn** - Core Backend Infrastructure & System Integrations.

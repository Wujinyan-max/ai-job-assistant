# User AI Configuration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add secure per-user AI provider configuration with Chat Completions and Responses API modes.

**Architecture:** Store one encrypted configuration per user, resolve it at request time, and pass an immutable runtime configuration into an API-mode-aware AI client. Keep the local mock engine as the no-key fallback.

**Tech Stack:** Java 17, Spring Boot 3.2, MyBatis-Plus, MySQL 8, AES-GCM, Vue 3, Element Plus.

**Spec:** `docs/superpowers/specs/2026-09-15-user-ai-configuration-design.md`

## Global Constraints

- API keys are never returned in plaintext or logged.
- API mode values are exactly `CHAT_COMPLETIONS` and `RESPONSES`.
- User data remains isolated by the authenticated `user_id`.
- Existing mock behavior remains available when no user key exists.

---

### Task 1: Encryption and protocol adapter

**Files:**
- Create: `backend/src/main/java/com/jobassistant/ai/ApiKeyCipher.java`
- Create: `backend/src/main/java/com/jobassistant/ai/AiRuntimeConfig.java`
- Modify: `backend/src/main/java/com/jobassistant/ai/AiClient.java`
- Test: `backend/src/test/java/com/jobassistant/ai/ApiKeyCipherTest.java`
- Test: `backend/src/test/java/com/jobassistant/ai/AiClientProtocolTest.java`

- [ ] Write failing tests for AES-GCM round-trip, nondeterministic ciphertext, endpoints, request bodies, and response extraction.
- [ ] Run focused Maven tests and verify expected failures.
- [ ] Implement the cipher and both protocol adapters.
- [ ] Run focused tests until green.

### Task 2: User configuration persistence and API

**Files:**
- Modify: `sql/schema.sql`
- Create: `backend/src/main/java/com/jobassistant/entity/AiUserConfig.java`
- Create: `backend/src/main/java/com/jobassistant/mapper/AiUserConfigMapper.java`
- Create: `backend/src/main/java/com/jobassistant/dto/AiConfigSaveDTO.java`
- Modify: `backend/src/main/java/com/jobassistant/vo/AiConfigVO.java`
- Create: `backend/src/main/java/com/jobassistant/service/AiConfigService.java`
- Create: `backend/src/main/java/com/jobassistant/service/impl/AiConfigServiceImpl.java`
- Modify: `backend/src/main/java/com/jobassistant/controller/AiController.java`
- Modify: `backend/src/main/java/com/jobassistant/service/impl/AiServiceImpl.java`

- [ ] Write failing service tests for masked reads, key preservation, and user isolation.
- [ ] Add the table, entity, DTO, mapper, service, and controller endpoints.
- [ ] Route all AI operations through the authenticated user's runtime config.
- [ ] Run backend tests until green.

### Task 3: Configuration user interface

**Files:**
- Modify: `frontend/src/api/index.js`
- Modify: `frontend/src/views/AiView.vue`
- Modify: `frontend/tests/prototype-ui.test.mjs`

- [ ] Add failing UI structure assertions for provider, API mode, Base URL, model, and masked API Key controls.
- [ ] Add the settings drawer, provider presets, save/test actions, and live mode badge.
- [ ] Run UI tests and production build.

### Task 4: Runtime verification

**Files:**
- Modify: `backend/src/main/resources/application.yml`
- Modify: `README.md`

- [ ] Document `AI_CONFIG_ENCRYPTION_KEY` and the supported modes.
- [ ] Run the full backend test suite and frontend UI tests/build.
- [ ] Start backend and frontend, verify ports and health, then report URLs.

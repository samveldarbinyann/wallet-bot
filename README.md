# Wallet Bot

Backend of a custodial multi-chain crypto wallet that lives inside Telegram as a Mini App.

A user opens the bot, taps a button and lands in the Mini App with wallets in six blockchains already created —
no sign-up, email or KYC: the Telegram account is the identity. Private keys are generated server-side and stored
encrypted in the database.

This repository contains the backend only; the Next.js Mini App frontend is a separate project.

## Features

- **Portfolio** — balances across 14 networks/tokens with prices, 24h change and charts
- **Deposits** — detected via webhooks (Alchemy, Helius, TonAPI) with polling scanners as a safety net,
  deduplicated by transaction hash
- **Send** — native coins and stablecoins with fee estimation, idempotency and "send max" recomputed at broadcast time
- **Cross-chain swaps** — through the SimpleSwap aggregator, guarded by a circuit breaker
- **Security** — Telegram `initData` HMAC auth with replay protection, JWT access/refresh tokens, 6-digit PIN,
  email 2FA, one-time sessions for private-key export, rate limiting on every sensitive endpoint
- **Notifications** — in-app, Telegram push and email; admin broadcasts
- **Extras** — address book, crypto news (RSS), admin panel, EN/RU localisation served by the backend

## Supported networks

| Base network | Tokens                        |
|--------------|-------------------------------|
| Ethereum     | USDT, USDC (ERC-20)           |
| BNB Chain    | USDT, USDC (BEP-20)           |
| Bitcoin      | —                             |
| Solana       | USDT, USDC (SPL)              |
| TON          | USDT (Jetton)                 |
| TRON         | USDT (TRC-20)                 |

## Tech stack

- **Java 21**, **Spring Boot 4** (WebMVC, Data JPA, Security, Cache, Actuator, Mail)
- **PostgreSQL** + **Liquibase** (schema managed only through migrations, `ddl-auto: validate`)
- Blockchain: **web3j** (EVM), **bitcoinj**, **solanaj**, **ton4j**, **BouncyCastle** (TRON secp256k1)
- **telegrambots** for the bot, **JJWT**, **Bucket4j** + **Caffeine**, **Resilience4j**, **MapStruct**, **ZXing**, **ROME**
- Tests: **JUnit 5**, **Mockito**, **Spring MockMvc**, **H2**, **ArchUnit**

## Architecture

Package-per-feature: every module is a bounded context with its own `api` / `application` / `domain` /
`infrastructure` layers.

```
com.example.walletbot
├── user, wallet, transaction, deposit, asset, swap,
│   export, notification, addressbook, news, admin, telegram   ← feature modules
├── platform   ← blockchain clients, web security, encryption, logging
└── shared     ← constants, exceptions, utilities, events
```

Module boundaries are enforced by ArchUnit (`ModuleBoundaryTest`).

## Getting started

Requirements: JDK 21, Docker.

```bash
# create .env in the repo root with the variables from DOCUMENTATION.md, section 22
docker compose up -d    # PostgreSQL + backend on 127.0.0.1:8080
```

Or run only the database in Docker and the backend locally:

```bash
docker compose up -d db
./mvnw spring-boot:run
```

## Tests

```bash
./mvnw test                          # unit + slice + ArchUnit tests, no external services needed
./mvnw test -Pintegration-tests      # also runs the full-context test (requires PostgreSQL)
```

## Documentation

[`DOCUMENTATION.md`](DOCUMENTATION.md) (in Russian) covers every flow in depth: wallet generation, authentication,
sending, swaps, deposit detection, notifications, caching, security, database schema, background jobs and
configuration.

## License

[MIT](LICENSE)

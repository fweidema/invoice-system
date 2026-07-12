# Roadmap

Diese Roadmap beschreibt den aktuellen Stand und die naechsten sinnvollen Entwicklungsschritte fuer **invoice-system**.

## Abgeschlossen

- [x] Sprint 001: Domain Model
- [x] Sprint 002: Project Documentation
- [x] Sprint 003: Document Import Pipeline
- [x] Sprint 004: OCR Text Extraction
- [x] Sprint 005: AI Abstraction
- [x] Sprint 006: Classification / Extraction Split
- [x] Sprint 007: Architecture Refactoring
- [x] Sprint 008: Prompt and Schema Management
- [x] Sprint 009: AI Infrastructure
- [x] Sprint 010: AI Request / Response
- [x] Sprint 011: Invoice Domain Mapping
- [x] Sprint 012: Invoice Validation
- [x] Sprint 013: Document Processing Workflow
- [x] Sprint 014: SQLite Persistence
- [x] Sprint 016: Duplicate Detection
- [x] Sprint 017: Archive Service
- [x] Sprint 018: Document Scenarios
- [x] Sprint 019: Batch Processing
- [x] Sprint 020: Configuration
- [x] Sprint 021: CLI Facade
- [x] Sprint 022: OpenAI Provider Integration
- [x] Sprint 023: Production Readiness and Technical Cleanup
- [x] Sprint 024: External Configuration and Operating Profiles
- [x] Sprint 025: Docker- und VPS-Deployment
- [x] Sprint 027: OpenAI-End-to-End-Testdokumentation und Testprotokoll-Vorlage

## Aktueller Stand

`invoice-worker` kann lokal PDF-Dokumente importieren, optional OCR ausfuehren, Rechnungsdaten ueber Mock-AI oder OpenAI analysieren, Ergebnisse validieren, Dubletten erkennen, in SQLite persistieren und Dokumente im Dateisystem archivieren. Der echte OpenAI-Betrieb ist als kontrollierter Ein-Dokument-Lauf mit Fake- oder anonymisierten Dokumenten dokumentiert.

## Naechste Schritte

- Kontrollierten OpenAI-End-to-End-Lauf auf dem VPS mit genau einem Fake-Dokument durchfuehren und protokollieren.
- Optionalen OpenAI-Integrationstest hinter Profil oder Tag ergaenzen, falls nach dem echten Lauf ein reproduzierbarer Bedarf entsteht.
- Operative Fehlercodes und CLI-Exit-Codes weiter schaerfen.
- REST-API erst nach stabiler CLI- und Konfigurationsbasis planen.
- Web UI erst nach REST-API und Betriebsmodell bewerten.

## Zurueckgestellt

- Scheduler
- n8n-Integration
- Kostenabrechnung und Tokenstatistik
- Multi-Tenant-Unterstuetzung
- Weitere AI-Provider

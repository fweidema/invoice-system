(() => {
    "use strict";

    const refreshIntervalMillis = 60000;
    const state = {
        invoices: [],
        history: [],
        selectedKey: null,
        timer: null
    };

    const elements = {
        serverStatus: document.querySelector("#server-status"),
        serverStatusText: document.querySelector("#server-status-text"),
        apiStatus: document.querySelector("#api-status"),
        currentTime: document.querySelector("#current-time"),
        invoiceCount: document.querySelector("#invoice-count"),
        historyCount: document.querySelector("#history-count"),
        errorBanner: document.querySelector("#error-banner"),
        historyTable: document.querySelector("#history-table"),
        invoiceTable: document.querySelector("#invoice-table"),
        historyEmpty: document.querySelector("#history-empty"),
        invoiceEmpty: document.querySelector("#invoice-empty"),
        historyUpdated: document.querySelector("#history-updated"),
        invoiceUpdated: document.querySelector("#invoice-updated"),
        detailType: document.querySelector("#detail-type"),
        detailList: document.querySelector("#detail-list")
    };

    document.addEventListener("DOMContentLoaded", () => {
        updateClock();
        void refreshData();
        state.timer = window.setInterval(() => {
            updateClock();
            void refreshData();
        }, refreshIntervalMillis);
    });

    async function refreshData() {
        try {
            const [health, invoices, history] = await Promise.all([
                fetchJson("/api/health"),
                fetchJson("/api/invoices"),
                fetchJson("/api/processing-history")
            ]);
            state.invoices = Array.isArray(invoices) ? invoices : [];
            state.history = Array.isArray(history) ? history : [];
            setOnline(health && health.status === "UP");
            renderAll();
            clearError();
        } catch (error) {
            setOnline(false);
            showError("Die REST-API ist aktuell nicht erreichbar. Vorhandene Daten bleiben sichtbar.");
        }
    }

    async function fetchJson(url) {
        const response = await fetch(url, { headers: { "Accept": "application/json" } });
        if (!response.ok) {
            throw new Error("Request failed: " + response.status);
        }
        return response.json();
    }

    function renderAll() {
        elements.invoiceCount.textContent = String(state.invoices.length);
        elements.historyCount.textContent = String(state.history.length);
        renderInvoices();
        renderHistory();
        updateSelection();
        const timestamp = "Aktualisiert " + formatTime(new Date());
        elements.invoiceUpdated.textContent = timestamp;
        elements.historyUpdated.textContent = timestamp;
    }

    function renderInvoices() {
        elements.invoiceTable.replaceChildren();
        elements.invoiceEmpty.hidden = state.invoices.length > 0;
        state.invoices.forEach((invoice) => {
            const row = document.createElement("tr");
            row.dataset.key = "invoice:" + safeText(invoice.invoiceNumber);
            row.append(
                cell(formatDate(invoice.invoiceDate)),
                cell(nested(invoice, "supplier", "name") || "-"),
                cell(invoice.invoiceNumber || "-"),
                cell(formatMoney(invoice.grossAmount)),
                cell("Rechnung")
            );
            row.addEventListener("click", () => selectInvoice(invoice.invoiceNumber));
            elements.invoiceTable.append(row);
        });
    }

    function renderHistory() {
        elements.historyTable.replaceChildren();
        elements.historyEmpty.hidden = state.history.length > 0;
        state.history.slice().reverse().forEach((entry) => {
            const row = document.createElement("tr");
            row.dataset.key = "history:" + safeText(entry.documentId);
            row.append(
                cell(formatDateTime(entry.finishedAt || entry.startedAt)),
                cell(entry.originalFilename || "-"),
                statusCell(entry.status),
                cell("Verarbeitung"),
                cell(vendorForHistory(entry)),
                cell(amountForHistory(entry))
            );
            row.addEventListener("click", () => selectHistory(entry.documentId));
            elements.historyTable.append(row);
        });
    }

    async function selectInvoice(invoiceNumber) {
        state.selectedKey = "invoice:" + safeText(invoiceNumber);
        updateSelection();
        try {
            const invoice = await fetchJson("/api/invoices/" + encodeURIComponent(invoiceNumber));
            showDetails("Rechnung", [
                ["Rechnungsnummer", invoice.invoiceNumber],
                ["Lieferant", nested(invoice, "supplier", "name")],
                ["Rechnungsdatum", formatDate(invoice.invoiceDate)],
                ["Faelligkeit", formatDate(invoice.dueDate)],
                ["Betrag", formatMoney(invoice.grossAmount)],
                ["Kundennummer", invoice.customerNumber],
                ["Bestellnummer", invoice.orderNumber],
                ["Datei", invoice.originalFilename]
            ]);
        } catch (error) {
            showError("Details konnten nicht aktualisiert werden. Die vorhandenen Daten bleiben sichtbar.");
        }
    }

    function selectHistory(documentId) {
        state.selectedKey = "history:" + safeText(documentId);
        updateSelection();
        const entry = state.history.find((candidate) => candidate.documentId === documentId);
        if (!entry) {
            showError("Der Verarbeitungseintrag ist nicht mehr in den geladenen Daten enthalten.");
            return;
        }
        showDetails("Verarbeitung", [
            ["Datei", entry.originalFilename],
            ["Status", entry.status],
            ["Rechnungsnummer", entry.invoiceNumber],
            ["Erfolgreich", entry.successful ? "Ja" : "Nein"],
            ["Persistiert", entry.persisted ? "Ja" : "Nein"],
            ["Duplikat", entry.duplicateDetected ? "Ja" : "Nein"],
            ["Gestartet", formatDateTime(entry.startedAt)],
            ["Beendet", formatDateTime(entry.finishedAt)],
            ["Dauer", formatDuration(entry.durationMillis)],
            ["Fehler", entry.errorMessage]
        ]);
    }

    function showDetails(type, items) {
        elements.detailType.textContent = type;
        elements.detailList.replaceChildren();
        items.forEach(([label, value]) => {
            const wrapper = document.createElement("div");
            const term = document.createElement("dt");
            const description = document.createElement("dd");
            term.textContent = label;
            description.textContent = value || "-";
            wrapper.append(term, description);
            elements.detailList.append(wrapper);
        });
    }

    function updateSelection() {
        document.querySelectorAll("tbody tr").forEach((row) => {
            row.classList.toggle("selected", row.dataset.key === state.selectedKey);
        });
    }

    function setOnline(online) {
        elements.serverStatus.className = "status-dot " + (online ? "status-up" : "status-down");
        elements.serverStatusText.textContent = online ? "Server erreichbar" : "Server nicht erreichbar";
        elements.apiStatus.textContent = online ? "Erreichbar" : "Nicht erreichbar";
    }

    function showError(message) {
        elements.errorBanner.textContent = message;
        elements.errorBanner.hidden = false;
    }

    function clearError() {
        elements.errorBanner.textContent = "";
        elements.errorBanner.hidden = true;
    }

    function updateClock() {
        elements.currentTime.textContent = formatTime(new Date());
    }

    function cell(value) {
        const td = document.createElement("td");
        td.textContent = value || "-";
        return td;
    }

    function statusCell(status) {
        const td = document.createElement("td");
        const pill = document.createElement("span");
        pill.className = "status-pill " + statusClass(status);
        pill.textContent = status || "UNKNOWN";
        td.append(pill);
        return td;
    }

    function statusClass(status) {
        switch (status) {
            case "SUCCESS": return "status-success";
            case "DUPLICATE": return "status-duplicate";
            case "VALIDATION_FAILED": return "status-validation-failed";
            case "OCR_FAILED": return "status-ocr-failed";
            case "AI_FAILED": return "status-ai-failed";
            case "ERROR": return "status-error";
            default: return "status-default";
        }
    }

    function vendorForHistory(entry) {
        const invoice = invoiceForHistory(entry);
        return invoice ? nested(invoice, "supplier", "name") || "-" : "-";
    }

    function amountForHistory(entry) {
        const invoice = invoiceForHistory(entry);
        return invoice ? formatMoney(invoice.grossAmount) : "-";
    }

    function invoiceForHistory(entry) {
        if (!entry.invoiceNumber) {
            return null;
        }
        return state.invoices.find((invoice) => invoice.invoiceNumber === entry.invoiceNumber) || null;
    }

    function nested(object, first, second) {
        return object && object[first] ? object[first][second] : null;
    }

    function formatMoney(money) {
        if (!money || money.amount === null || money.amount === undefined) {
            return "-";
        }
        const currency = money.currency || "EUR";
        return new Intl.NumberFormat("de-DE", { style: "currency", currency }).format(Number(money.amount));
    }

    function formatDate(value) {
        if (!value) {
            return "-";
        }
        return new Intl.DateTimeFormat("de-DE").format(new Date(value));
    }

    function formatDateTime(value) {
        if (!value) {
            return "-";
        }
        return new Intl.DateTimeFormat("de-DE", { dateStyle: "short", timeStyle: "short" }).format(new Date(value));
    }

    function formatTime(value) {
        return new Intl.DateTimeFormat("de-DE", { timeStyle: "medium" }).format(value);
    }

    function formatDuration(value) {
        if (value === null || value === undefined) {
            return "-";
        }
        return value + " ms";
    }

    function safeText(value) {
        return value === null || value === undefined ? "" : String(value);
    }
})();
(() => {
    "use strict";

    const refreshIntervalMillis = 60000;
    const pageSize = 20;
    const state = {
        invoices: emptyPage("invoiceDate", "DESC"),
        history: emptyPage("finishedAt", "DESC"),
        invoicePage: 0,
        historyPage: 0,
        selectedKey: null
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
        detailList: document.querySelector("#detail-list"),
        historyQuery: document.querySelector("#history-query"),
        historyStatus: document.querySelector("#history-status"),
        historySort: document.querySelector("#history-sort"),
        historyDirection: document.querySelector("#history-direction"),
        historyPrev: document.querySelector("#history-prev"),
        historyNext: document.querySelector("#history-next"),
        historyPageInfo: document.querySelector("#history-page-info"),
        invoiceQuery: document.querySelector("#invoice-query"),
        invoiceSupplier: document.querySelector("#invoice-supplier"),
        invoiceSort: document.querySelector("#invoice-sort"),
        invoiceDirection: document.querySelector("#invoice-direction"),
        invoicePrev: document.querySelector("#invoice-prev"),
        invoiceNext: document.querySelector("#invoice-next"),
        invoicePageInfo: document.querySelector("#invoice-page-info")
    };

    document.addEventListener("DOMContentLoaded", () => {
        bindControls();
        updateClock();
        void refreshData();
        window.setInterval(() => {
            updateClock();
            void refreshData();
        }, refreshIntervalMillis);
    });

    function bindControls() {
        [elements.historyQuery, elements.historyStatus, elements.historySort, elements.historyDirection].forEach((control) => {
            control.addEventListener("input", () => {
                state.historyPage = 0;
                void refreshHistory();
            });
        });
        [elements.invoiceQuery, elements.invoiceSupplier, elements.invoiceSort, elements.invoiceDirection].forEach((control) => {
            control.addEventListener("input", () => {
                state.invoicePage = 0;
                void refreshInvoices();
            });
        });
        elements.historyPrev.addEventListener("click", () => changeHistoryPage(-1));
        elements.historyNext.addEventListener("click", () => changeHistoryPage(1));
        elements.invoicePrev.addEventListener("click", () => changeInvoicePage(-1));
        elements.invoiceNext.addEventListener("click", () => changeInvoicePage(1));
    }

    async function refreshData() {
        try {
            const health = await fetchJson("/api/health");
            setOnline(health && health.status === "UP");
            await Promise.all([refreshInvoices(), refreshHistory()]);
            clearError();
        } catch (error) {
            setOnline(false);
            showError("Die REST-API ist aktuell nicht erreichbar. Vorhandene Daten bleiben sichtbar.");
        }
    }

    async function refreshInvoices() {
        const page = await fetchJson("/api/invoices?" + invoiceQueryString());
        state.invoices = page;
        renderInvoices();
        elements.invoiceCount.textContent = String(page.totalElements || 0);
        elements.invoiceUpdated.textContent = "Aktualisiert " + formatTime(new Date());
        updateSelection();
    }

    async function refreshHistory() {
        const page = await fetchJson("/api/processing-history?" + historyQueryString());
        state.history = page;
        renderHistory();
        elements.historyCount.textContent = String(page.totalElements || 0);
        elements.historyUpdated.textContent = "Aktualisiert " + formatTime(new Date());
        updateSelection();
    }

    async function fetchJson(url) {
        const response = await fetch(url, { headers: { "Accept": "application/json" } });
        if (!response.ok) {
            throw new Error("Request failed: " + response.status);
        }
        return response.json();
    }

    function invoiceQueryString() {
        const parameters = new URLSearchParams();
        parameters.set("page", String(state.invoicePage));
        parameters.set("size", String(pageSize));
        parameters.set("sort", elements.invoiceSort.value);
        parameters.set("direction", elements.invoiceDirection.value);
        append(parameters, "q", elements.invoiceQuery.value);
        append(parameters, "supplier", elements.invoiceSupplier.value);
        return parameters.toString();
    }

    function historyQueryString() {
        const parameters = new URLSearchParams();
        parameters.set("page", String(state.historyPage));
        parameters.set("size", String(pageSize));
        parameters.set("sort", elements.historySort.value);
        parameters.set("direction", elements.historyDirection.value);
        append(parameters, "q", elements.historyQuery.value);
        append(parameters, "status", elements.historyStatus.value);
        return parameters.toString();
    }

    function append(parameters, name, value) {
        if (value && value.trim()) {
            parameters.set(name, value.trim());
        }
    }

    function renderInvoices() {
        const items = state.invoices.items || [];
        elements.invoiceTable.replaceChildren();
        elements.invoiceEmpty.hidden = items.length > 0;
        items.forEach((invoice) => {
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
        renderPagination(state.invoices, elements.invoicePrev, elements.invoiceNext, elements.invoicePageInfo);
    }

    function renderHistory() {
        const items = state.history.items || [];
        elements.historyTable.replaceChildren();
        elements.historyEmpty.hidden = items.length > 0;
        items.forEach((entry) => {
            const row = document.createElement("tr");
            row.dataset.key = "history:" + safeText(entry.documentId);
            row.append(
                cell(formatDateTime(entry.finishedAt || entry.startedAt)),
                cell(entry.originalFilename || "-"),
                statusCell(entry.status),
                cell(entry.invoiceNumber || "-"),
                cell(formatDuration(entry.durationMillis))
            );
            row.addEventListener("click", () => selectHistory(entry.documentId));
            elements.historyTable.append(row);
        });
        renderPagination(state.history, elements.historyPrev, elements.historyNext, elements.historyPageInfo);
    }

    function renderPagination(page, previous, next, label) {
        const totalPages = Math.max(page.totalPages || 0, 1);
        const currentPage = (page.page || 0) + 1;
        label.textContent = "Seite " + currentPage + " von " + totalPages;
        previous.disabled = currentPage <= 1;
        next.disabled = currentPage >= totalPages;
    }

    function changeHistoryPage(delta) {
        state.historyPage = Math.max(0, state.historyPage + delta);
        void refreshHistory();
    }

    function changeInvoicePage(delta) {
        state.invoicePage = Math.max(0, state.invoicePage + delta);
        void refreshInvoices();
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

    async function selectHistory(documentId) {
        state.selectedKey = "history:" + safeText(documentId);
        updateSelection();
        try {
            const entry = await fetchJson("/api/processing-history/" + encodeURIComponent(documentId));
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
        } catch (error) {
            showError("Details konnten nicht aktualisiert werden. Die vorhandenen Daten bleiben sichtbar.");
        }
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

    function emptyPage(sort, direction) {
        return { items: [], page: 0, size: pageSize, totalElements: 0, totalPages: 0, sort, direction };
    }
})();
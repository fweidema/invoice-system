(() => {
    "use strict";

    const refreshIntervalMillis = 60000;
    const defaultPageSize = 25;
    const defaultInvoiceSort = "importedAt";
    const defaultHistorySort = "startedAt";
    const defaultDirection = "DESC";

    const state = {
        invoices: emptyPage(defaultInvoiceSort, defaultDirection),
        history: emptyPage(defaultHistorySort, defaultDirection),
        invoicePage: 0,
        historyPage: 0,
        invoiceSize: defaultPageSize,
        historySize: defaultPageSize,
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
        historyFilters: document.querySelector("#history-filters"),
        historyQuery: document.querySelector("#history-query"),
        historyStatus: document.querySelector("#history-status"),
        historyDateFrom: document.querySelector("#history-date-from"),
        historyDateTo: document.querySelector("#history-date-to"),
        historySize: document.querySelector("#history-size"),
        historySort: document.querySelector("#history-sort"),
        historyDirection: document.querySelector("#history-direction"),
        historyReset: document.querySelector("#history-reset"),
        historyFirst: document.querySelector("#history-first"),
        historyPrev: document.querySelector("#history-prev"),
        historyNext: document.querySelector("#history-next"),
        historyLast: document.querySelector("#history-last"),
        historyPageInfo: document.querySelector("#history-page-info"),
        invoiceFilters: document.querySelector("#invoice-filters"),
        invoiceQuery: document.querySelector("#invoice-query"),
        invoiceSupplier: document.querySelector("#invoice-supplier"),
        invoiceNumber: document.querySelector("#invoice-number"),
        invoiceDateFrom: document.querySelector("#invoice-date-from"),
        invoiceDateTo: document.querySelector("#invoice-date-to"),
        invoiceSize: document.querySelector("#invoice-size"),
        invoiceSort: document.querySelector("#invoice-sort"),
        invoiceDirection: document.querySelector("#invoice-direction"),
        invoiceReset: document.querySelector("#invoice-reset"),
        invoiceFirst: document.querySelector("#invoice-first"),
        invoicePrev: document.querySelector("#invoice-prev"),
        invoiceNext: document.querySelector("#invoice-next"),
        invoiceLast: document.querySelector("#invoice-last"),
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
        elements.historyFilters.addEventListener("submit", (event) => {
            event.preventDefault();
            state.historyPage = 0;
            void refreshHistory();
        });
        [elements.historyStatus, elements.historySort, elements.historyDirection, elements.historySize].forEach((control) => {
            control.addEventListener("change", () => {
                state.historyPage = 0;
                state.historySize = selectedSize(elements.historySize);
                void refreshHistory();
            });
        });
        elements.historyReset.addEventListener("click", () => resetHistoryFilters());
        elements.historyFirst.addEventListener("click", () => goToHistoryPage(0));
        elements.historyPrev.addEventListener("click", () => goToHistoryPage(state.historyPage - 1));
        elements.historyNext.addEventListener("click", () => goToHistoryPage(state.historyPage + 1));
        elements.historyLast.addEventListener("click", () => goToHistoryPage(lastPageIndex(state.history)));

        elements.invoiceFilters.addEventListener("submit", (event) => {
            event.preventDefault();
            state.invoicePage = 0;
            void refreshInvoices();
        });
        [elements.invoiceSort, elements.invoiceDirection, elements.invoiceSize].forEach((control) => {
            control.addEventListener("change", () => {
                state.invoicePage = 0;
                state.invoiceSize = selectedSize(elements.invoiceSize);
                void refreshInvoices();
            });
        });
        elements.invoiceReset.addEventListener("click", () => resetInvoiceFilters());
        elements.invoiceFirst.addEventListener("click", () => goToInvoicePage(0));
        elements.invoicePrev.addEventListener("click", () => goToInvoicePage(state.invoicePage - 1));
        elements.invoiceNext.addEventListener("click", () => goToInvoicePage(state.invoicePage + 1));
        elements.invoiceLast.addEventListener("click", () => goToInvoicePage(lastPageIndex(state.invoices)));
    }

    async function refreshData() {
        const healthResult = await settle(() => fetchJson("/api/health"));
        if (healthResult.status === "fulfilled") {
            setOnline(healthResult.value && healthResult.value.status === "UP");
        } else {
            setOnline(false);
        }

        const results = await Promise.allSettled([refreshInvoices(), refreshHistory()]);
        const failures = results.filter((result) => result.status === "rejected");
        if (healthResult.status === "rejected" || failures.length > 0) {
            showError("Einige Daten konnten nicht aktualisiert werden. Vorhandene Daten bleiben sichtbar.");
            return;
        }
        clearError();
    }

    async function refreshInvoices(retried = false) {
        const page = await fetchJson("/api/invoices?" + invoiceQueryString());
        if (shouldReloadPage(state.invoicePage, page.totalPages, retried)) {
            state.invoicePage = correctedPage(page.totalPages);
            return refreshInvoices(true);
        }
        state.invoices = page;
        renderInvoices();
        elements.invoiceCount.textContent = String(page.totalElements || 0);
        elements.invoiceUpdated.textContent = "Aktualisiert " + formatTime(new Date());
        updateSelection();
    }

    async function refreshHistory(retried = false) {
        const page = await fetchJson("/api/processing-history?" + historyQueryString());
        if (shouldReloadPage(state.historyPage, page.totalPages, retried)) {
            state.historyPage = correctedPage(page.totalPages);
            return refreshHistory(true);
        }
        state.history = page;
        renderHistory();
        elements.historyCount.textContent = String(page.totalElements || 0);
        elements.historyUpdated.textContent = "Aktualisiert " + formatTime(new Date());
        updateSelection();
    }

    async function settle(action) {
        try {
            return { status: "fulfilled", value: await action() };
        } catch (error) {
            return { status: "rejected", reason: error };
        }
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
        parameters.set("size", String(state.invoiceSize));
        parameters.set("sort", elements.invoiceSort.value);
        parameters.set("direction", elements.invoiceDirection.value);
        append(parameters, "q", elements.invoiceQuery.value);
        append(parameters, "supplier", elements.invoiceSupplier.value);
        append(parameters, "invoiceNumber", elements.invoiceNumber.value);
        append(parameters, "dateFrom", elements.invoiceDateFrom.value);
        append(parameters, "dateTo", elements.invoiceDateTo.value);
        return parameters.toString();
    }

    function historyQueryString() {
        const parameters = new URLSearchParams();
        parameters.set("page", String(state.historyPage));
        parameters.set("size", String(state.historySize));
        parameters.set("sort", elements.historySort.value);
        parameters.set("direction", elements.historyDirection.value);
        append(parameters, "q", elements.historyQuery.value);
        append(parameters, "status", elements.historyStatus.value);
        append(parameters, "dateFrom", elements.historyDateFrom.value);
        append(parameters, "dateTo", elements.historyDateTo.value);
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
        renderPagination(state.invoices, elements.invoiceFirst, elements.invoicePrev, elements.invoiceNext, elements.invoiceLast, elements.invoicePageInfo);
    }

    function renderHistory() {
        const items = state.history.items || [];
        elements.historyTable.replaceChildren();
        elements.historyEmpty.hidden = items.length > 0;
        items.forEach((entry) => {
            const row = document.createElement("tr");
            row.dataset.key = "history:" + safeText(entry.documentId);
            row.append(
                cell(formatDateTime(entry.startedAt)),
                cell(entry.originalFilename || "-"),
                statusCell(entry.status),
                cell(entry.invoiceNumber || "-"),
                cell(formatDuration(entry.durationMillis))
            );
            row.addEventListener("click", () => selectHistory(entry.documentId));
            elements.historyTable.append(row);
        });
        renderPagination(state.history, elements.historyFirst, elements.historyPrev, elements.historyNext, elements.historyLast, elements.historyPageInfo);
    }

    function renderPagination(page, first, previous, next, last, label) {
        const totalPages = page.totalPages || 0;
        if (totalPages === 0) {
            label.textContent = "Seite 0 von 0";
            [first, previous, next, last].forEach((button) => { button.disabled = true; });
            return;
        }
        const currentPage = (page.page || 0) + 1;
        label.textContent = "Seite " + currentPage + " von " + totalPages;
        first.disabled = currentPage <= 1;
        previous.disabled = currentPage <= 1;
        next.disabled = currentPage >= totalPages;
        last.disabled = currentPage >= totalPages;
    }

    function shouldReloadPage(currentPage, totalPages, retried) {
        return !retried && totalPages > 0 && currentPage >= totalPages;
    }

    function correctedPage(totalPages) {
        return totalPages > 0 ? totalPages - 1 : 0;
    }

    function lastPageIndex(page) {
        return page.totalPages > 0 ? page.totalPages - 1 : 0;
    }

    function goToHistoryPage(page) {
        state.historyPage = Math.max(0, page);
        void refreshHistory();
    }

    function goToInvoicePage(page) {
        state.invoicePage = Math.max(0, page);
        void refreshInvoices();
    }

    function resetHistoryFilters() {
        elements.historyFilters.reset();
        elements.historySort.value = defaultHistorySort;
        elements.historyDirection.value = defaultDirection;
        elements.historySize.value = String(defaultPageSize);
        state.historyPage = 0;
        state.historySize = defaultPageSize;
        void refreshHistory();
    }

    function resetInvoiceFilters() {
        elements.invoiceFilters.reset();
        elements.invoiceSort.value = defaultInvoiceSort;
        elements.invoiceDirection.value = defaultDirection;
        elements.invoiceSize.value = String(defaultPageSize);
        state.invoicePage = 0;
        state.invoiceSize = defaultPageSize;
        void refreshInvoices();
    }

    function selectedSize(select) {
        return Number.parseInt(select.value, 10) || defaultPageSize;
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
        return new Intl.DateTimeFormat("de-DE").format(new Date(value + "T00:00:00"));
    }

    function formatDateTime(value) {
        if (!value) {
            return "-";
        }
        return new Intl.DateTimeFormat("de-DE", {
            dateStyle: "short",
            timeStyle: "short"
        }).format(new Date(value));
    }

    function formatTime(value) {
        return new Intl.DateTimeFormat("de-DE", { timeStyle: "medium" }).format(value);
    }

    function formatDuration(value) {
        if (value === null || value === undefined) {
            return "-";
        }
        return String(value) + " ms";
    }

    function safeText(value) {
        return value || "";
    }

    function emptyPage(sort, direction) {
        return { items: [], page: 0, size: defaultPageSize, totalElements: 0, totalPages: 0, sort, direction };
    }
})();

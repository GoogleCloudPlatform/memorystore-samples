/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Module-level logging
const log = {
  info: (...args) =>
    console.log(
      "%c[Leaderboard]",
      "color: #2196F3; font-weight: bold",
      ...args
    ),
  error: (...args) =>
    console.error(
      "%c[Leaderboard]",
      "color: #F44336; font-weight: bold",
      ...args
    ),
  warn: (...args) =>
    console.warn(
      "%c[Leaderboard]",
      "color: #FF9800; font-weight: bold",
      ...args
    ),
  debug: (...args) =>
    console.debug(
      "%c[Leaderboard]",
      "color: #4CAF50; font-weight: bold",
      ...args
    ),
};

log.info("Module loaded");

const PAGE_SIZE = 25;
let currentPage = 0;
let isLoading = false;
let leaderboardData = [];

// Central DOM reference object for easier maintenance
const elements = {
  entriesContainer: document.getElementById("entries-container"),
  filter: document.getElementById("filter"),
  search: document.getElementById("search"),
  prevPage: document.getElementById("prev-page"),
  nextPage: document.getElementById("next-page"),
  pageNumber: document.getElementById("page-number"),
  usernameInput: document.getElementById("username-input"),
  scoreInput: document.getElementById("score-input"),
  addEntry: document.getElementById("add-entry"),
  fromCache: document.getElementById("from-cache"),
  timeToFetch: document.getElementById("time-to-fetch"),
  totalResults: document.getElementById("total-results"),
};

// Event Listeners
elements.filter.addEventListener("change", () => refreshLeaderboard());
elements.search.addEventListener("input", () => refreshLeaderboard());
elements.prevPage.addEventListener("click", previousPage);
elements.nextPage.addEventListener("click", nextPage);
elements.addEntry.addEventListener("click", addEntry);

async function fetchLeaderboard() {
  isLoading = true;
  const startTime = Date.now();

  try {
    // URLSearchParams automatically encodes URL parameters
    const params = new URLSearchParams({
      position: currentPage * PAGE_SIZE,
      size: PAGE_SIZE,
      orderBy: elements.filter.value,
    });

    if (elements.search.value) {
      params.append("username", elements.search.value);
    }

    log.debug("Fetching leaderboard", { params: Object.fromEntries(params) });
    const response = await fetch(`/api/leaderboard?${params}`);
    const data = await response.json();
    log.debug("Received data", {
      entries: data.entries?.length,
      totalCount: data.totalCount,
      fromCache: data.fromCache,
      raw: data,
    });

    // Update analytics after we have the data
    updateAnalytics(data.fromCache, Date.now() - startTime, data);
    return data;
  } catch (error) {
    log.error("Fetch error:", error);
    return { entries: [], totalCount: 0 };
  } finally {
    isLoading = false;
  }
}

function updateAnalytics(cacheStatus, duration, data) {
  // Update leaderboardData first
  if (data) {
    leaderboardData = data;
  }

  // Then update UI elements
  elements.fromCache.textContent = cacheStatus ? "Yes" : "No";
  elements.timeToFetch.textContent = duration ? `${duration}ms` : "-";
  elements.totalResults.textContent = leaderboardData?.totalCount ?? 0;

  log.debug("Analytics updated", {
    cacheStatus,
    duration,
    totalCount: leaderboardData?.totalCount,
    leaderboardData,
  });
}

async function refreshLeaderboard() {
  currentPage = 0;
  const data = await fetchLeaderboard();
  updatePagination();
  renderEntries();
}

function renderEntries() {
  elements.entriesContainer.innerHTML = isLoading
    ? loadingTemplate()
    : leaderboardData?.entries?.length
    ? entriesTemplate()
    : emptyTemplate();
}

function loadingTemplate() {
  return `<div class="text-center py-8 text-gray-500">Loading entries...</div>`;
}

function emptyTemplate() {
  return `<div class="text-center py-8 text-gray-500">No entries found</div>`;
}

// Template functions separate markup generation
function entriesTemplate() {
  return `
    <ul class="divide-y">
      ${leaderboardData.entries
        .map(
          (entry) => `
        <li class="py-3 hover:bg-gray-50">
          <div class="flex justify-between items-center">
            <span class="w-16">${entry.position + 1}.</span>
            <span class="flex-1">${entry.username}</span>
            <span class="w-24 text-right font-mono text-blue-600">${
              entry.score
            }</span>
          </div>
        </li>
      `
        )
        .join("")}
    </ul>
  `;
}

async function addEntry() {
  const username = elements.usernameInput.value.trim();
  const score = parseInt(elements.scoreInput.value);

  if (!username || isNaN(score)) {
    log.warn("Invalid entry attempt", { username, score });
    alert("Please enter valid name and score");
    return;
  }

  try {
    log.debug("Adding entry", { username, score });
    const response = await fetch("/api/leaderboard", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, score }),
    });

    if (response.ok) {
      log.info("Entry added successfully");
      elements.usernameInput.value = "";
      elements.scoreInput.value = "";
      refreshLeaderboard();
    }
  } catch (error) {
    log.error("Submission error:", error);
  }
}

function updatePagination() {
  // Always use totalCount for pagination, regardless of filtering
  const totalPages = Math.ceil((leaderboardData?.totalCount ?? 0) / PAGE_SIZE);

  log.debug("Pagination update", {
    totalCount: leaderboardData?.totalCount,
    entriesCount: leaderboardData?.entries?.length,
    pageSize: PAGE_SIZE,
    totalPages,
    currentPage,
    isFiltering: elements.search.value.trim() !== "",
  });

  elements.pageNumber.textContent = currentPage + 1;

  // Disable previous button if on first page
  elements.prevPage.disabled = currentPage === 0;

  // Disable next button if:
  // 1. We have no entries OR
  // 2. Current entries are less than page size (indicating it's the last page)
  elements.nextPage.disabled =
    !leaderboardData?.entries?.length ||
    leaderboardData.entries.length < PAGE_SIZE;
}

async function nextPage() {
  const totalPages = Math.ceil((leaderboardData?.totalCount ?? 0) / PAGE_SIZE);

  log.debug("Next page check", {
    currentPage,
    totalPages,
    totalCount: leaderboardData?.totalCount,
    entriesCount: leaderboardData?.entries?.length,
    canProceed:
      currentPage < totalPages - 1 && leaderboardData?.entries?.length > 0,
  });

  if (currentPage >= totalPages - 1 || !leaderboardData?.entries?.length)
    return;
  currentPage++;
  await loadPage();
}

async function previousPage() {
  log.debug("Previous page check", {
    currentPage,
    canProceed: currentPage > 0,
  });

  if (currentPage === 0) return;
  currentPage = Math.max(0, currentPage - 1);
  await loadPage();
}

async function loadPage() {
  log.debug("Loading page", {
    currentPage,
    pageSize: PAGE_SIZE,
    isFiltering: elements.search.value.trim() !== "",
  });

  const data = await fetchLeaderboard();
  updatePagination();
  renderEntries();
}

// Initial load
log.info("Starting initial leaderboard load");
refreshLeaderboard();

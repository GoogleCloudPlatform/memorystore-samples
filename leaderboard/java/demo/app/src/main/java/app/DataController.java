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

package app;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.resps.Tuple;

@Controller
public class DataController {

  /** Logger for the DataController. */
  private static final Logger LOGGER = LoggerFactory
      .getLogger(DataController.class);

  /** Repository for persisting leaderboard entries. */
  private final LeaderboardRepository leaderboardRepository;

  /** Redis client for caching leaderboard data. */
  private final Jedis jedis;

  /**
   * Constructs a new DataController.
   *
   * @param redisClient Redis client for caching
   * @param repository  Repository for persistence
   */
  public DataController(final Jedis redisClient,
      final LeaderboardRepository repository) {
    this.leaderboardRepository = repository;
    this.jedis = redisClient;
  }

  /**
   * Get the leaderboard entries starting from the given position.
   *
   * @param position The starting position of the entries to search.
   * @param orderBy  The order of the entries.
   * @param pageSize The number of entries to return.
   * @param username The username to check the rank of.
   * @return The leaderboard entries.
   * @throws IllegalArgumentException if pagination parameters are invalid
   * @throws ServiceException         if there's a failure accessing the
   *                                  leaderboard
   */
  public LeaderboardResponse getLeaderboard(
      final long position, final OrderByType orderBy,
      final long pageSize, final String username) {

    // Input validation
    if (position < 0 || pageSize <= 0) {
      throw new IllegalArgumentException(
          "Invalid pagination parameters: position must be >= 0 and "
              + "pageSize must be > 0");
    }

    String cacheKey = Global.LEADERBOARD_ENTRIES_KEY;
    long maxPosition = position + pageSize - 1;

    try {
      // Initialize the cache if it's empty
      boolean cacheUpdated = this.initializeCache();

      // Set the cache status for the front end
      int cacheStatus = cacheUpdated
          ? FromCacheType.FROM_DB.getValue()
          : FromCacheType.FULL_CACHE.getValue();

      // Get total size once
      long totalSize = jedis.zcard(cacheKey);

      // If we have a username, search for the user's rank
      if (username != null) {
        Long userRank = jedis.zrevrank(cacheKey, username);
        if (userRank != null) {
          long startPos;
          long endPos;
          long totalResults;
          if (orderBy == OrderByType.HIGH_TO_LOW) {
            startPos = userRank + position;
            endPos = startPos + pageSize - 1;
            // For descending order, total results is from user's position to the end
            totalResults = totalSize - userRank;
          } else {
            long userPosInAscending = totalSize - userRank - 1;
            startPos = userPosInAscending + position;
            endPos = startPos + pageSize - 1;
            // For ascending order, total results is from start to user's position
            totalResults = userPosInAscending + 1;
          }

          // Ensure we don't exceed the total size
          if (endPos >= totalSize) {
            endPos = totalSize - 1;
          }

          // If start position is beyond end position, return empty list
          if (startPos > endPos) {
            return new LeaderboardResponse(
                new ArrayList<>(),
                cacheStatus,
                totalResults);
          }

          return new LeaderboardResponse(
              getEntries(cacheKey, startPos, endPos,
                  orderBy == OrderByType.HIGH_TO_LOW),
              cacheStatus,
              totalResults);
        }
      }

      // Get the leaderboard entries depending on the order
      List<LeaderboardEntry> leaderboardList = getEntries(
          cacheKey,
          position,
          maxPosition,
          orderBy == OrderByType.HIGH_TO_LOW);

      return new LeaderboardResponse(leaderboardList, cacheStatus, totalSize);

    } catch (JedisException e) {
      // Log and handle Redis failures
      throw new DataControllerException("Failed to retrieve leaderboard", e);
    }
  }

  private List<LeaderboardEntry> getEntries(
      final String cacheKey,
      final long position,
      final long maxPosition,
      final boolean isDescending) {

    try {
      // Define an object
      List<Tuple> entries = new ArrayList<>();

      // Use zrevrangeWithScores to get the entries in descending order
      if (isDescending) {
        entries = new ArrayList<>(
            jedis.zrevrangeWithScores(cacheKey, position, maxPosition));
      }

      // If zrangeWithScores is used, the entries are in ascending order
      if (!isDescending) {
        entries = new ArrayList<>(
            jedis.zrangeWithScores(cacheKey, position, maxPosition));
      }

      List<LeaderboardEntry> newEntries = new ArrayList<>();
      long totalSize = !isDescending ? jedis.zcard(cacheKey) : 0;

      for (int i = 0; i < entries.size(); i++) {
        Tuple e = entries.get(i);

        long overallPosition = position + i;
        if (!isDescending) {
          overallPosition = totalSize - overallPosition - 1;
        }

        newEntries.add(
            new LeaderboardEntry(
                e.getElement(),
                e.getScore(),
                overallPosition));
      }

      return newEntries;

    } catch (JedisException e) {
      // Log and handle Redis failures
      throw new DataControllerException(
          "Failed to retrieve leaderboard entries", e);
    }
  }

  /**
   * Initializes the leaderboard cache if it is empty.
   *
   * @return {@code true} if the cache was initialized, {@code false} if it was
   *         already populated.
   * @throws DataControllerException if there's a failure
   *                                 initializing the cache
   */
  private boolean initializeCache() {
    try {
      if (this.jedis.zcard(Global.LEADERBOARD_ENTRIES_KEY) > 0) {
        return false;
      }

      List<LeaderboardEntry> entries = this.leaderboardRepository.getEntries();

      if (!entries.isEmpty()) {
        for (LeaderboardEntry entry : entries) {
          this.jedis.zadd(
              Global.LEADERBOARD_ENTRIES_KEY,
              entry.getScore(),
              entry.getUsername());
        }
      }

      return true;

    } catch (JedisException e) {
      // Log and handle Redis failures
      throw new DataControllerException(
          "Failed to initialize leaderboard cache", e);
    } catch (Exception e) {
      // Handle repository failures
      throw new DataControllerException(
          "Failed to load leaderboard data from repository", e);
    }
  }

  /**
   * Creates or updates a leaderboard entry with the given username and score.
   * Only updates the entry if the new score is higher than the current score.
   *
   * @param username The username of the entry
   * @param score    The score to set
   * @throws IllegalArgumentException if username is null or empty, or if score
   *                                  is null
   * @throws ServiceException         if there's a failure updating the
   *                                  leaderboard
   */
  public void createOrUpdate(final String username, final Double score) {
    // Input validation
    if (username == null || username.trim().isEmpty()) {
      throw new IllegalArgumentException("Username cannot be null or empty");
    }
    if (score == null) {
      throw new IllegalArgumentException("Score cannot be null");
    }

    try {
      // See if score is higher than the current score
      Double currentScore = this.jedis.zscore(
          Global.LEADERBOARD_ENTRIES_KEY, username);
      if (currentScore != null && currentScore >= score) {
        return;
      }

      // Update database first (source of truth)
      this.leaderboardRepository.update(username, score);

      // Try to update cache, but don't fail the operation if cache update fails
      try {
        this.jedis.zadd(
            Global.LEADERBOARD_ENTRIES_KEY,
            score,
            username);
      } catch (JedisException cacheEx) {
        LOGGER.warn(
            "Failed to update cache for user: {} with score: {}.",
            username, score, cacheEx);

        try {
          this.jedis.del(Global.LEADERBOARD_ENTRIES_KEY);
        } catch (Exception ignored) {
        }
      }

    } catch (JedisException e) {
      // catch redis failures during the initial score check
      throw new DataControllerException(
          "Failed to check current score in leaderboard", e);
    } catch (Exception e) {
      // catch repository (database) failures
      throw new DataControllerException(
          "Failed to persist leaderboard entry", e);
    }
  }
}

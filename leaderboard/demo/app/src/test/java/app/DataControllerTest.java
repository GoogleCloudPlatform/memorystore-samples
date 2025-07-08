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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.resps.Tuple;

@ExtendWith(MockitoExtension.class)
class DataControllerTest {

  @Mock
  private LeaderboardRepository leaderboardRepository;
  @Mock
  private Jedis jedis;
  private DataController dataController;

  @BeforeEach
  void setUp() {
    dataController = new DataController(jedis, leaderboardRepository);
  }

  @Nested
  @DisplayName("Testing getLeaderboard() method")
  class GetLeaderboardTests {

    @Test
    @DisplayName("Should return paginated entries in descending order")
    void testGetLeaderboard_DescendingOrder() {
      final long position = 0;
      final long pageSize = 2;
      final String cacheKey = Global.LEADERBOARD_ENTRIES_KEY;
      final List<Tuple> mockEntries = new ArrayList<>();
      mockEntries.add(new Tuple("user1", 100.0));
      mockEntries.add(new Tuple("user2", 90.0));

      given(jedis.zcard(cacheKey)).willReturn(3L);
      given(jedis.zrevrangeWithScores(cacheKey, position, position + pageSize - 1))
          .willReturn(mockEntries);

      final LeaderboardResponse response = dataController.getLeaderboard(
          position,
          OrderByType.HIGH_TO_LOW,
          pageSize,
          null);

      assertEquals(2, response.getEntries().size());
      assertEquals("user1", response.getEntries().get(0).getUsername());
      assertEquals(100.0, response.getEntries().get(0).getScore());
      assertEquals("user2", response.getEntries().get(1).getUsername());
      assertEquals(90.0, response.getEntries().get(1).getScore());
    }

    @Test
    @DisplayName("Should return paginated entries in ascending order")
    void testGetLeaderboard_AscendingOrder() {
      final long position = 0;
      final long pageSize = 2;
      final String cacheKey = Global.LEADERBOARD_ENTRIES_KEY;
      final List<Tuple> mockEntries = new ArrayList<>();
      mockEntries.add(new Tuple("user3", 80.0));
      mockEntries.add(new Tuple("user2", 90.0));

      given(jedis.zcard(cacheKey)).willReturn(3L);
      given(jedis.zrangeWithScores(cacheKey, position, position + pageSize - 1))
          .willReturn(mockEntries);

      final LeaderboardResponse response = dataController.getLeaderboard(
          position,
          OrderByType.LOW_TO_HIGH,
          pageSize,
          null);

      assertEquals(2, response.getEntries().size());
      assertEquals("user3", response.getEntries().get(0).getUsername());
      assertEquals(80.0, response.getEntries().get(0).getScore());
      assertEquals("user2", response.getEntries().get(1).getUsername());
      assertEquals(90.0, response.getEntries().get(1).getScore());
    }

    @Test
    @DisplayName("Should return filtered entries by username with pagination")
    void testGetLeaderboard_UsernameFilter() {
      final long position = 0;
      final long pageSize = 2;
      final String username = "user1";
      final String cacheKey = Global.LEADERBOARD_ENTRIES_KEY;
      final List<Tuple> mockEntries = new ArrayList<>();
      mockEntries.add(new Tuple("user1", 100.0));
      mockEntries.add(new Tuple("user1_alt", 95.0));

      given(jedis.zcard(cacheKey)).willReturn(3L);
      given(jedis.zrevrangeWithScores(cacheKey, position, position + pageSize - 1))
          .willReturn(mockEntries);

      final LeaderboardResponse response = dataController.getLeaderboard(
          position,
          OrderByType.HIGH_TO_LOW,
          pageSize,
          username);

      assertEquals(2, response.getEntries().size());
      assertTrue(response.getEntries().get(0).getUsername().startsWith("user1"));
      assertTrue(response.getEntries().get(1).getUsername().startsWith("user1"));
    }

    @Test
    @DisplayName("Should initialize cache if empty")
    void testGetLeaderboard_InitializeCache() {
      final String cacheKey = Global.LEADERBOARD_ENTRIES_KEY;
      final List<LeaderboardEntry> dbEntries = new ArrayList<>();
      dbEntries.add(new LeaderboardEntry("user1", 100.0));
      dbEntries.add(new LeaderboardEntry("user2", 90.0));

      given(jedis.zcard(cacheKey)).willReturn(0L);
      given(leaderboardRepository.getEntries()).willReturn(dbEntries);

      dataController.getLeaderboard(0, OrderByType.HIGH_TO_LOW, 10, null);

      verify(jedis).zadd(cacheKey, 100.0, "user1");
      verify(jedis).zadd(cacheKey, 90.0, "user2");
    }

    @Test
    @DisplayName("Should return correct cache status")
    void testGetLeaderboard_CacheStatus() {
      final String cacheKey = Global.LEADERBOARD_ENTRIES_KEY;
      final List<Tuple> mockEntries = new ArrayList<>();
      mockEntries.add(new Tuple("user1", 100.0));

      given(jedis.zcard(cacheKey)).willReturn(1L);
      given(jedis.zrevrangeWithScores(cacheKey, 0, 0)).willReturn(mockEntries);

      final LeaderboardResponse response = dataController.getLeaderboard(
          0,
          OrderByType.HIGH_TO_LOW,
          1,
          null);

      assertEquals(FromCacheType.FULL_CACHE.getValue(), response.getFromCache());
    }

    @Test
    @DisplayName("Should start from user's position when filtering by username")
    void testGetLeaderboard_UsernameFilterStartsFromUser() {
      final long position = 0;
      final long pageSize = 2;
      final String username = "user1";
      final String cacheKey = Global.LEADERBOARD_ENTRIES_KEY;
      final List<Tuple> mockEntries = new ArrayList<>();
      mockEntries.add(new Tuple("user1", 100.0));
      mockEntries.add(new Tuple("user2", 90.0));

      given(jedis.zrevrank(cacheKey, username)).willReturn(2L);
      given(jedis.zcard(cacheKey)).willReturn(10L);
      given(jedis.zrevrangeWithScores(anyString(), anyLong(), anyLong()))
          .willReturn(mockEntries);

      final LeaderboardResponse response = dataController.getLeaderboard(
          position,
          OrderByType.HIGH_TO_LOW,
          pageSize,
          username);

      assertEquals(
          2,
          response.getEntries().size(),
          "Expected 2 entries in response, but got " + response.getEntries().size());

      verify(jedis).zrevrangeWithScores(cacheKey, 2, 3);
    }

    @Test
    @DisplayName("Should paginate from user's position when filtering by username")
    void testGetLeaderboard_UsernameFilterPaginatesFromUser() {
      final long position = 1;
      final long pageSize = 2;
      final String username = "user1";
      final String cacheKey = Global.LEADERBOARD_ENTRIES_KEY;
      final List<Tuple> mockEntries = new ArrayList<>();
      mockEntries.add(new Tuple("user3", 80.0));
      mockEntries.add(new Tuple("user4", 70.0));

      given(jedis.zrevrank(cacheKey, username)).willReturn(2L);
      given(jedis.zcard(cacheKey)).willReturn(10L);
      given(jedis.zrevrangeWithScores(anyString(), anyLong(), anyLong()))
          .willReturn(mockEntries);

      final LeaderboardResponse response = dataController.getLeaderboard(
          position,
          OrderByType.HIGH_TO_LOW,
          pageSize,
          username);

      assertEquals(
          2,
          response.getEntries().size(),
          "Expected 2 entries in response, but got " + response.getEntries().size());

      verify(jedis).zrevrangeWithScores(cacheKey, 3, 4);
    }

    @Test
    @DisplayName("Should respect sorting when filtering by username")
    void testGetLeaderboard_UsernameFilterRespectsSorting() {
      final long position = 0;
      final long pageSize = 2;
      final String username = "user1";
      final String cacheKey = Global.LEADERBOARD_ENTRIES_KEY;
      final List<Tuple> mockEntries = new ArrayList<>();
      mockEntries.add(new Tuple("user3", 120.0));
      mockEntries.add(new Tuple("user4", 130.0));

      given(jedis.zrevrank(cacheKey, username)).willReturn(2L);
      given(jedis.zcard(cacheKey)).willReturn(10L);
      given(jedis.zrangeWithScores(anyString(), anyLong(), anyLong()))
          .willReturn(mockEntries);

      final LeaderboardResponse response = dataController.getLeaderboard(
          position,
          OrderByType.LOW_TO_HIGH,
          pageSize,
          username);

      assertEquals(
          2,
          response.getEntries().size(),
          "Expected 2 entries in response, but got " + response.getEntries().size());

      verify(jedis).zrangeWithScores(cacheKey, 7, 8);
      verify(jedis, never()).zrevrangeWithScores(anyString(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("Should respect both pagination and sorting when filtering by username")
    void testGetLeaderboard_UsernameFilterRespectsPaginationAndSorting() {
      final long position = 1;
      final long pageSize = 2;
      final String username = "user1";
      final String cacheKey = Global.LEADERBOARD_ENTRIES_KEY;
      final List<Tuple> mockEntries = new ArrayList<>();
      mockEntries.add(new Tuple("user5", 140.0));
      mockEntries.add(new Tuple("user6", 150.0));

      given(jedis.zrevrank(cacheKey, username)).willReturn(2L);
      given(jedis.zcard(cacheKey)).willReturn(10L);
      given(jedis.zrangeWithScores(anyString(), anyLong(), anyLong()))
          .willReturn(mockEntries);

      final LeaderboardResponse response = dataController.getLeaderboard(
          position,
          OrderByType.LOW_TO_HIGH,
          pageSize,
          username);

      assertEquals(
          2,
          response.getEntries().size(),
          "Expected 2 entries in response, but got " + response.getEntries().size());

      verify(jedis).zrangeWithScores(cacheKey, 8L, 9L);
      verify(jedis, never()).zrevrangeWithScores(anyString(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("Should handle pagination with large dataset and user filter")
    void testGetLeaderboard_LargeDatasetPagination() {
      final long pageSize = 10;
      final String username = "user14"; // We'll look for the 14th user
      final String cacheKey = Global.LEADERBOARD_ENTRIES_KEY;

      // Mock 200 entries in the leaderboard
      given(jedis.zcard(cacheKey)).willReturn(200L);

      // Mock that user14 is at position 13 (0-based index)
      given(jedis.zrevrank(cacheKey, username)).willReturn(13L);

      // First page (position 0) - should show users 14 through 5
      List<Tuple> firstPageEntries = new ArrayList<>();
      for (int i = 14; i >= 5; i--) {
        firstPageEntries.add(new Tuple("user" + i, (double) (200 - i)));
      }

      // Second page (position 1) - should show users 4 through 1
      List<Tuple> secondPageEntries = new ArrayList<>();
      for (int i = 4; i >= 1; i--) {
        secondPageEntries.add(new Tuple("user" + i, (double) (200 - i)));
      }

      // Calculate the actual ranges that will be used
      // For user at position 13 in descending order:
      // - In ascending order, their position is 200 - 13 - 1 = 186
      // - First page starts at 186 + 0 = 186
      // - Second page starts at 186 + 1 = 187
      lenient().doReturn(firstPageEntries).when(jedis).zrangeWithScores(cacheKey, 186L, 195L);
      lenient().doReturn(secondPageEntries).when(jedis).zrangeWithScores(cacheKey, 187L, 196L);

      // Test first page
      LeaderboardResponse firstPageResponse = dataController.getLeaderboard(
          0,
          OrderByType.LOW_TO_HIGH,
          pageSize,
          username);

      // Verify first page results
      assertEquals(10, firstPageResponse.getEntries().size(), "First page should have 10 entries");
      for (int i = 0; i < 10; i++) {
        assertEquals("user" + (14 - i), firstPageResponse.getEntries().get(i).getUsername());
        assertEquals(200.0 - (14 - i), firstPageResponse.getEntries().get(i).getScore());
      }

      // Test second page
      LeaderboardResponse secondPageResponse = dataController.getLeaderboard(
          1,
          OrderByType.LOW_TO_HIGH,
          pageSize,
          username);

      // Verify second page results
      assertEquals(4, secondPageResponse.getEntries().size(), "Second page should have 4 entries");
      for (int i = 0; i < 4; i++) {
        assertEquals("user" + (4 - i), secondPageResponse.getEntries().get(i).getUsername());
        assertEquals(200.0 - (4 - i), secondPageResponse.getEntries().get(i).getScore());
      }

      // Verify Redis calls
      verify(jedis).zrangeWithScores(cacheKey, 186L, 195L); // First page
      verify(jedis).zrangeWithScores(cacheKey, 187L, 196L); // Second page
    }

    @Test
    @DisplayName("Should handle empty leaderboard")
    void testGetLeaderboard_EmptyLeaderboard() {
      final String cacheKey = Global.LEADERBOARD_ENTRIES_KEY;
      final List<LeaderboardEntry> emptyList = new ArrayList<>();

      given(jedis.zcard(cacheKey)).willReturn(0L);
      given(leaderboardRepository.getEntries()).willReturn(emptyList);

      final LeaderboardResponse response = dataController.getLeaderboard(
          0,
          OrderByType.HIGH_TO_LOW,
          10,
          null);

      assertEquals(0, response.getEntries().size());
      assertEquals(FromCacheType.FROM_DB.getValue(), response.getFromCache());
    }

    @Test
    @DisplayName("Should handle null username in filter")
    void testGetLeaderboard_NullUsernameFilter() {
      final long position = 0;
      final long pageSize = 2;
      final String cacheKey = Global.LEADERBOARD_ENTRIES_KEY;
      final List<Tuple> mockEntries = new ArrayList<>();
      mockEntries.add(new Tuple("user1", 100.0));
      mockEntries.add(new Tuple("user2", 90.0));

      given(jedis.zcard(cacheKey)).willReturn(2L);
      given(jedis.zrevrangeWithScores(cacheKey, position, position + pageSize - 1))
          .willReturn(mockEntries);

      final LeaderboardResponse response = dataController.getLeaderboard(
          position,
          OrderByType.HIGH_TO_LOW,
          pageSize,
          null);

      assertEquals(2, response.getEntries().size());
      verify(jedis, never()).zrevrank(anyString(), anyString());
    }

    @Test
    @DisplayName("Should throw exception for negative position parameter")
    void testGetLeaderboard_NegativePosition() {
      final long position = -1;
      final long pageSize = 2;

      IllegalArgumentException exception = assertThrows(
          IllegalArgumentException.class,
          () -> dataController.getLeaderboard(position, OrderByType.HIGH_TO_LOW, pageSize, null));

      assertEquals("Invalid pagination parameters: position must be >= 0 and pageSize must be > 0",
          exception.getMessage());
      verify(jedis, never()).zcard(anyString());
    }

    @Test
    @DisplayName("Should throw exception for zero page size")
    void testGetLeaderboard_ZeroPageSize() {
      final long position = 0;
      final long pageSize = 0;

      IllegalArgumentException exception = assertThrows(
          IllegalArgumentException.class,
          () -> dataController.getLeaderboard(position, OrderByType.HIGH_TO_LOW, pageSize, null));

      assertEquals("Invalid pagination parameters: position must be >= 0 and pageSize must be > 0",
          exception.getMessage());
      verify(jedis, never()).zcard(anyString());
    }

    @Test
    @DisplayName("Should handle non-existent username when filtering")
    void testGetLeaderboard_NonExistentUsernameFilter() {
      final long position = 0;
      final long pageSize = 2;
      final String username = "nonexistent";
      final String cacheKey = Global.LEADERBOARD_ENTRIES_KEY;
      final List<Tuple> mockEntries = new ArrayList<>();
      mockEntries.add(new Tuple("user1", 100.0));
      mockEntries.add(new Tuple("user2", 90.0));

      given(jedis.zrevrank(cacheKey, username)).willReturn(null);
      given(jedis.zcard(cacheKey)).willReturn(10L);
      given(jedis.zrevrangeWithScores(cacheKey, position, position + pageSize - 1))
          .willReturn(mockEntries);

      final LeaderboardResponse response = dataController.getLeaderboard(
          position,
          OrderByType.HIGH_TO_LOW,
          pageSize,
          username);

      assertEquals(2, response.getEntries().size());
      assertEquals("user1", response.getEntries().get(0).getUsername());
      assertEquals("user2", response.getEntries().get(1).getUsername());
      verify(jedis).zrevrank(cacheKey, username);
      verify(jedis).zrevrangeWithScores(cacheKey, 0L, 1L);
    }

    @Test
    @DisplayName("Should handle request beyond last page")
    void testGetLeaderboard_BeyondLastPage() {
      final long position = 100;
      final long pageSize = 10;
      final String cacheKey = Global.LEADERBOARD_ENTRIES_KEY;
      final List<Tuple> mockEntries = new ArrayList<>();

      given(jedis.zcard(cacheKey)).willReturn(50L);
      given(jedis.zrevrangeWithScores(cacheKey, position, position + pageSize - 1))
          .willReturn(mockEntries);

      final LeaderboardResponse response = dataController.getLeaderboard(
          position,
          OrderByType.HIGH_TO_LOW,
          pageSize,
          null);

      assertEquals(0, response.getEntries().size());
      verify(jedis).zrevrangeWithScores(cacheKey, 100L, 109L);
    }

    @Test
    @DisplayName("Should handle empty result from Redis range query")
    void testGetLeaderboard_EmptyRangeResult() {
      final long position = 5;
      final long pageSize = 2;
      final String cacheKey = Global.LEADERBOARD_ENTRIES_KEY;
      final List<Tuple> mockEntries = new ArrayList<>();

      given(jedis.zcard(cacheKey)).willReturn(5L);
      given(jedis.zrevrangeWithScores(cacheKey, position, position + pageSize - 1))
          .willReturn(mockEntries);

      final LeaderboardResponse response = dataController.getLeaderboard(
          position,
          OrderByType.HIGH_TO_LOW,
          pageSize,
          null);

      assertEquals(0, response.getEntries().size());
      assertEquals(FromCacheType.FULL_CACHE.getValue(), response.getFromCache());
    }

    @Test
    @DisplayName("Should handle boundary case for ascending order calculation")
    void testGetLeaderboard_AscendingOrderBoundary() {
      final long position = 0;
      final long pageSize = 1;
      final String cacheKey = Global.LEADERBOARD_ENTRIES_KEY;
      final List<Tuple> mockEntries = new ArrayList<>();
      mockEntries.add(new Tuple("lowestScoreUser", 1.0));

      given(jedis.zcard(cacheKey)).willReturn(100L);
      given(jedis.zrangeWithScores(cacheKey, position, position + pageSize - 1))
          .willReturn(mockEntries);

      final LeaderboardResponse response = dataController.getLeaderboard(
          position,
          OrderByType.LOW_TO_HIGH,
          pageSize,
          null);

      assertEquals(1, response.getEntries().size());
      assertEquals("lowestScoreUser", response.getEntries().get(0).getUsername());
      assertEquals(1.0, response.getEntries().get(0).getScore());
      assertEquals(99L, response.getEntries().get(0).getPosition());
    }

    @Test
    @DisplayName("Should throw ServiceException when Redis fails during cache initialization")
    void testGetLeaderboard_RedisFailure() {
      final String cacheKey = Global.LEADERBOARD_ENTRIES_KEY;

      when(jedis.zcard(cacheKey)).thenThrow(new JedisException("Redis connection failed"));

      DataControllerException exception = assertThrows(
          DataControllerException.class,
          () -> dataController.getLeaderboard(0, OrderByType.HIGH_TO_LOW, 10, null));

      assertEquals("Failed to initialize leaderboard cache", exception.getMessage());
      assertTrue(exception.getCause() instanceof JedisException);
    }

    @Test
    @DisplayName("Should throw ServiceException when Redis fails during entry retrieval")
    void testGetLeaderboard_RedisFailureDuringRetrieval() {
      final String cacheKey = Global.LEADERBOARD_ENTRIES_KEY;

      given(jedis.zcard(cacheKey)).willReturn(10L); // Cache is already initialized
      when(jedis.zrevrangeWithScores(cacheKey, 0L, 9L)).thenThrow(new JedisException("Redis connection failed"));

      DataControllerException exception = assertThrows(
          DataControllerException.class,
          () -> dataController.getLeaderboard(0, OrderByType.HIGH_TO_LOW, 10, null));

      assertEquals("Failed to retrieve leaderboard entries", exception.getMessage());
      assertTrue(exception.getCause() instanceof JedisException);
    }

    @Test
    @DisplayName("Should throw ServiceException when repository fails during cache initialization")
    void testGetLeaderboard_RepositoryFailure() {
      final String cacheKey = Global.LEADERBOARD_ENTRIES_KEY;

      given(jedis.zcard(cacheKey)).willReturn(0L);
      when(leaderboardRepository.getEntries()).thenThrow(new RuntimeException("Database connection failed"));

      DataControllerException exception = assertThrows(
          DataControllerException.class,
          () -> dataController.getLeaderboard(0, OrderByType.HIGH_TO_LOW, 10, null));

      assertEquals("Failed to load leaderboard data from repository", exception.getMessage());
    }

    @Test
    @DisplayName("Should calculate correct total count for filtered results in descending order")
    void testGetLeaderboard_FilteredTotalCountDescending() {
      final long position = 0;
      final long pageSize = 5;
      final String username = "user5";
      final String cacheKey = Global.LEADERBOARD_ENTRIES_KEY;
      final List<Tuple> mockEntries = new ArrayList<>();
      mockEntries.add(new Tuple("user5", 100.0));
      mockEntries.add(new Tuple("user6", 90.0));

      // User is at position 4 (0-based) in descending order
      given(jedis.zrevrank(cacheKey, username)).willReturn(4L);
      given(jedis.zcard(cacheKey)).willReturn(10L);
      given(jedis.zrevrangeWithScores(cacheKey, 4L, 8L)).willReturn(mockEntries);

      final LeaderboardResponse response = dataController.getLeaderboard(
          position,
          OrderByType.HIGH_TO_LOW,
          pageSize,
          username);

      // Total count should be 6 (from position 4 to end of list)
      assertEquals(6L, response.getTotalCount());
      assertEquals(2, response.getEntries().size());
    }

    @Test
    @DisplayName("Should calculate correct total count for filtered results in ascending order")
    void testGetLeaderboard_FilteredTotalCountAscending() {
      final long position = 0;
      final long pageSize = 5;
      final String username = "user5";
      final String cacheKey = Global.LEADERBOARD_ENTRIES_KEY;
      final List<Tuple> mockEntries = new ArrayList<>();
      mockEntries.add(new Tuple("user5", 100.0));
      mockEntries.add(new Tuple("user4", 90.0));

      // User is at position 4 (0-based) in descending order
      // In ascending order, this becomes position 5 (10 - 4 - 1)
      given(jedis.zrevrank(cacheKey, username)).willReturn(4L);
      given(jedis.zcard(cacheKey)).willReturn(10L);
      given(jedis.zrangeWithScores(cacheKey, 5L, 9L)).willReturn(mockEntries);

      final LeaderboardResponse response = dataController.getLeaderboard(
          position,
          OrderByType.LOW_TO_HIGH,
          pageSize,
          username);

      // Total count should be 6 (from start to position 5)
      assertEquals(6L, response.getTotalCount());
      assertEquals(2, response.getEntries().size());
    }

    @Test
    @DisplayName("Should handle pagination beyond available results for filtered view")
    void testGetLeaderboard_FilteredPaginationBeyondResults() {
      final long position = 10; // Try to get results beyond available
      final long pageSize = 5;
      final String username = "user5";
      final String cacheKey = Global.LEADERBOARD_ENTRIES_KEY;

      // User is at position 4 (0-based) in descending order
      given(jedis.zrevrank(cacheKey, username)).willReturn(4L);
      given(jedis.zcard(cacheKey)).willReturn(10L);

      final LeaderboardResponse response = dataController.getLeaderboard(
          position,
          OrderByType.HIGH_TO_LOW,
          pageSize,
          username);

      // Should return empty list but maintain correct total count
      assertEquals(0, response.getEntries().size());
      assertEquals(6L, response.getTotalCount());
    }

    @Test
    @DisplayName("Should handle edge case when filtered user is at the end of list")
    void testGetLeaderboard_FilteredUserAtEnd() {
      final long position = 0;
      final long pageSize = 5;
      final String username = "user9";
      final String cacheKey = Global.LEADERBOARD_ENTRIES_KEY;
      final List<Tuple> mockEntries = new ArrayList<>();
      mockEntries.add(new Tuple("user9", 10.0));

      // User is at position 9 (0-based) in descending order
      given(jedis.zrevrank(cacheKey, username)).willReturn(9L);
      given(jedis.zcard(cacheKey)).willReturn(10L);
      given(jedis.zrevrangeWithScores(cacheKey, 9L, 9L)).willReturn(mockEntries);

      final LeaderboardResponse response = dataController.getLeaderboard(
          position,
          OrderByType.HIGH_TO_LOW,
          pageSize,
          username);

      // Total count should be 1 (just the user)
      assertEquals(1L, response.getTotalCount());
      assertEquals(1, response.getEntries().size());
    }

    @Test
    @DisplayName("Should handle edge case when filtered user is at the start of list")
    void testGetLeaderboard_FilteredUserAtStart() {
      final long position = 0;
      final long pageSize = 5;
      final String username = "user0";
      final String cacheKey = Global.LEADERBOARD_ENTRIES_KEY;
      final List<Tuple> mockEntries = new ArrayList<>();
      mockEntries.add(new Tuple("user0", 100.0));
      mockEntries.add(new Tuple("user1", 90.0));

      // User is at position 0 (0-based) in descending order
      given(jedis.zrevrank(cacheKey, username)).willReturn(0L);
      given(jedis.zcard(cacheKey)).willReturn(10L);
      given(jedis.zrevrangeWithScores(cacheKey, 0L, 4L)).willReturn(mockEntries);

      final LeaderboardResponse response = dataController.getLeaderboard(
          position,
          OrderByType.HIGH_TO_LOW,
          pageSize,
          username);

      // Total count should be 10 (entire list)
      assertEquals(10L, response.getTotalCount());
      assertEquals(2, response.getEntries().size());
    }
  }

  @Nested
  @DisplayName("Testing createOrUpdate() method")
  class CreateOrUpdateTests {

    @Test
    @DisplayName("Should update score if new score is higher")
    void testCreateOrUpdate_HigherScore() {
      String username = "user1";
      Double newScore = 100.0;
      String cacheKey = Global.LEADERBOARD_ENTRIES_KEY;

      given(jedis.zscore(cacheKey, username)).willReturn(90.0);

      dataController.createOrUpdate(username, newScore);

      verify(leaderboardRepository).update(username, newScore);
      verify(jedis).zadd(cacheKey, newScore, username);
    }

    @Test
    @DisplayName("Should not update score if new score is lower")
    void testCreateOrUpdate_LowerScore() {
      String username = "user1";
      Double newScore = 80.0;
      String cacheKey = Global.LEADERBOARD_ENTRIES_KEY;

      given(jedis.zscore(cacheKey, username)).willReturn(90.0);

      dataController.createOrUpdate(username, newScore);

      verify(leaderboardRepository, never()).update(username, newScore);
      verify(jedis, never()).zadd(cacheKey, newScore, username);
    }

    @Test
    @DisplayName("Should create new entry if user doesn't exist")
    void testCreateOrUpdate_NewUser() {
      String username = "newUser";
      Double score = 100.0;
      String cacheKey = Global.LEADERBOARD_ENTRIES_KEY;

      given(jedis.zscore(cacheKey, username)).willReturn(null);

      dataController.createOrUpdate(username, score);

      verify(leaderboardRepository).update(username, score);
      verify(jedis).zadd(cacheKey, score, username);
    }

    @Test
    @DisplayName("Should throw exception for null username")
    void testCreateOrUpdate_NullUsername() {
      IllegalArgumentException exception = assertThrows(
          IllegalArgumentException.class,
          () -> dataController.createOrUpdate(null, 100.0));

      assertEquals("Username cannot be null or empty", exception.getMessage());
      verify(jedis, never()).zscore(anyString(), anyString());
    }

    @Test
    @DisplayName("Should throw exception for empty username")
    void testCreateOrUpdate_EmptyUsername() {
      IllegalArgumentException exception = assertThrows(
          IllegalArgumentException.class,
          () -> dataController.createOrUpdate("   ", 100.0));

      assertEquals("Username cannot be null or empty", exception.getMessage());
      verify(jedis, never()).zscore(anyString(), anyString());
    }

    @Test
    @DisplayName("Should throw exception for null score")
    void testCreateOrUpdate_NullScore() {
      IllegalArgumentException exception = assertThrows(
          IllegalArgumentException.class,
          () -> dataController.createOrUpdate("user1", null));

      assertEquals("Score cannot be null", exception.getMessage());
      verify(jedis, never()).zscore(anyString(), anyString());
    }

    @Test
    @DisplayName("Should throw ServiceException when Redis fails")
    void testCreateOrUpdate_RedisFailure() {
      String username = "user1";
      Double score = 100.0;
      String cacheKey = Global.LEADERBOARD_ENTRIES_KEY;

      when(jedis.zscore(cacheKey, username)).thenThrow(new JedisException("Redis connection failed"));

      DataControllerException exception = assertThrows(
          DataControllerException.class,
          () -> dataController.createOrUpdate(username, score));

      assertEquals("Failed to check current score in leaderboard", exception.getMessage());
      assertTrue(exception.getCause() instanceof JedisException);
    }

    @Test
    @DisplayName("Should throw ServiceException when repository fails")
    void testCreateOrUpdate_RepositoryFailure() {
      String username = "user1";
      Double score = 100.0;
      String cacheKey = Global.LEADERBOARD_ENTRIES_KEY;

      given(jedis.zscore(cacheKey, username)).willReturn(null);
      doThrow(new RuntimeException("Database error")).when(leaderboardRepository).update(username, score);

      DataControllerException exception = assertThrows(
          DataControllerException.class,
          () -> dataController.createOrUpdate(username, score));

      assertEquals("Failed to persist leaderboard entry", exception.getMessage());
    }
  }
}
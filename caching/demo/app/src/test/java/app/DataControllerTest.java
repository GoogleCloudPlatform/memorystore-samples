/*
- * Copyright 2025 Google LLC
- *
- * Licensed under the Apache License, Version 2.0 (the "License");
- * you may not use this file except in compliance with the License.
- * You may obtain a copy of the License at
- *
- * http://www.apache.org/licenses/LICENSE-2.0
- *
- * Unless required by applicable law or agreed to in writing, software
- * distributed under the License is distributed on an "AS IS" BASIS,
- * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
- * See the License for the specific language governing permissions and
- * limitations under the License.
- */

package app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import redis.clients.jedis.Jedis;

@ExtendWith(MockitoExtension.class)
class DataControllerTest {

  @Mock
  private ItemsRepository itemsRepository;

  @Mock
  private Jedis jedis;

  private DataController dataController;

  @BeforeEach
  void setUp() {
    dataController = new DataController(itemsRepository, jedis);
  }

  // ----------------------------------------------------
  // get() tests
  // ----------------------------------------------------
  @Nested
  @DisplayName("Testing get() method")
  class GetTests {

    @Test
    @DisplayName("Should return item from cache if it exists in cache")
    void testGet_ItemInCache() {
      long itemId = 1;
      String itemIdStr = Long.toString(itemId);
      String cachedData =
          "{\"id\":1,\"name\":\"Cached Item\",\"description\":\"Cached description\",\"price\":10.5}";
      Item cachedItem = Item.fromJsonString(cachedData);

      given(jedis.get(itemIdStr)).willReturn(cachedData);

      Item result = dataController.get(itemId);

      verify(itemsRepository, never()).get(anyLong());
      assertEquals(cachedItem.getId(), result.getId());
      assertEquals(cachedItem.getName(), result.getName());
      assertTrue(result.isFromCache());
    }

    @Test
    @DisplayName("Should return item from database and cache it if not in cache")
    void testGet_ItemNotInCache() {
      long itemId = 2;
      String itemIdStr = Long.toString(itemId);
      Item dbItem = new Item(2L, "Database Item", "From DB", 15.99);

      given(jedis.get(itemIdStr)).willReturn(null);
      given(itemsRepository.get(itemId)).willReturn(Optional.of(dbItem));

      Item result = dataController.get(itemId);

      verify(jedis).setex(itemIdStr, DataController.DEFAULT_TTL, dbItem.toJsonObject().toString());
      assertEquals(dbItem.getId(), result.getId());
      assertEquals(dbItem.getName(), result.getName());
      assertFalse(result.isFromCache());
    }

    @Test
    @DisplayName("Should return null if item does not exist in cache or database")
    void testGet_ItemNotFound() {
      long itemId = 3;
      String itemIdStr = Long.toString(itemId);

      given(jedis.get(itemIdStr)).willReturn(null);
      given(itemsRepository.get(itemId)).willReturn(Optional.empty());

      Item result = dataController.get(itemId);

      verify(jedis, never()).setex(anyString(), eq(DataController.DEFAULT_TTL), anyString());
      assertNull(result);
    }
  }

  // ----------------------------------------------------
  // create() tests
  // ----------------------------------------------------
  @Nested
  @DisplayName("Testing create() method")
  class CreateTests {

    @Test
    @DisplayName("Should create item in cache and database")
    void testCreate() {
      Item item = new Item("New Item", "New Description", 20.0);
      given(itemsRepository.create(item)).willReturn(0L);

      long result = dataController.create(item);

      Item expectedItem = new Item(0L, item.getName(), item.getDescription(), item.getPrice());
      verify(jedis).setex(Long.toString(result), DataController.DEFAULT_TTL,
          expectedItem.toJsonObject().toString());
      assertEquals(0L, result);
    }
  }

  // ----------------------------------------------------
  // delete() tests
  // ----------------------------------------------------
  @Nested
  @DisplayName("Testing delete() method")
  class DeleteTests {

    @Test
    @DisplayName("Should delete item from both cache and database")
    void testDelete_ItemExists() {
      long itemId = 6;
      String itemIdStr = Long.toString(itemId);

      given(jedis.del(itemIdStr)).willReturn(1L);

      dataController.delete(itemId);

      verify(itemsRepository).delete(itemId);
      verify(jedis).del(itemIdStr);
    }

    @Test
    @DisplayName("Should delete item from database even if not found in cache")
    void testDelete_ItemNotInCache() {
      long itemId = 7;
      String itemIdStr = Long.toString(itemId);

      given(jedis.del(itemIdStr)).willReturn(0L);

      dataController.delete(itemId);

      verify(itemsRepository).delete(itemId);
      verify(jedis).del(itemIdStr);
    }
  }

  // ----------------------------------------------------
  // exists() tests
  // ----------------------------------------------------
  @Nested
  @DisplayName("Testing exists() method")
  class ExistsTests {

    @Test
    @DisplayName("Should return true if item exists in cache")
    void testExists_ItemInCache() {
      long itemId = 8;
      String itemIdStr = Long.toString(itemId);

      given(jedis.exists(itemIdStr)).willReturn(true);

      boolean result = dataController.exists(itemId);

      assertTrue(result);
      verify(itemsRepository, never()).exists(anyLong());
    }

    @Test
    @DisplayName("Should return true if item exists in database")
    void testExists_ItemInDatabase() {
      long itemId = 9;
      String itemIdStr = Long.toString(itemId);

      given(jedis.exists(itemIdStr)).willReturn(false);
      given(itemsRepository.exists(itemId)).willReturn(true);

      boolean result = dataController.exists(itemId);

      assertTrue(result);
    }

    @Test
    @DisplayName("Should return false if item does not exist in cache or database")
    void testExists_ItemNotFound() {
      long itemId = 10;
      String itemIdStr = Long.toString(itemId);

      given(jedis.exists(itemIdStr)).willReturn(false);
      given(itemsRepository.exists(itemId)).willReturn(false);

      boolean result = dataController.exists(itemId);

      assertFalse(result);
    }
  }
}

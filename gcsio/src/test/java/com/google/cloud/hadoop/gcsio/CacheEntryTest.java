/**
 * Copyright 2014 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.hadoop.gcsio;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.api.client.util.Clock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * UnitTests for CacheEntry class.
 */
@RunWith(JUnit4.class)
public class CacheEntryTest {
  private Clock mockClock;
  private StorageResourceId bucketResourceId;
  private StorageResourceId objectResourceId;
  private GoogleCloudStorageItemInfo bucketInfo;
  private GoogleCloudStorageItemInfo objectInfo;

  @Before
  public void setUp() {
    mockClock = mock(Clock.class);

    CacheEntry.setClock(mockClock);

    bucketInfo = DirectoryListCacheTestUtils.createBucketInfo("foo-bucket");
    bucketResourceId = bucketInfo.getResourceId();
    objectInfo = DirectoryListCacheTestUtils.createObjectInfo("foo-bucket", "bar-object");
    objectResourceId = objectInfo.getResourceId();
  }

  @Test
  public void testConstructorThrowsWhenStorageResourceIsNull() {
    assertThrows(IllegalArgumentException.class, () -> new CacheEntry((StorageResourceId) null));
  }

  @Test
  public void testConstructorThrowsWhenStorageResourceIsRoot() {
    assertThrows(IllegalArgumentException.class, () -> new CacheEntry(StorageResourceId.ROOT));
  }

  @Test
  public void testConstructorThrowsWhenStorageItemInfoIsNull() {
    assertThrows(
        IllegalArgumentException.class, () -> new CacheEntry((GoogleCloudStorageItemInfo) null));
  }

  @Test
  public void testConstructorThrowsWhenStorageItemInfoIsRoot() {
    assertThrows(
        IllegalArgumentException.class, () -> new CacheEntry(GoogleCloudStorageItemInfo.ROOT_INFO));
  }

  @Test
  public void testConstructorThrowsWhenBucketStorageItemInfoIsNotFound() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CacheEntry(GoogleCloudStorageImpl.createItemInfoForNotFound(bucketResourceId)));
  }

  @Test
  public void testConstructorThrowsWhenObjectStorageItemInfoIsNotFound() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new CacheEntry(GoogleCloudStorageImpl.createItemInfoForNotFound(objectResourceId)));
  }

  /**
   * Helper for verifying the values of the provided resourceId, itemInfo, creationTime, and
   * lastUpdatedTime once the itemInfo has been properly set in the CacheEntry. Also verifies
   * the stickiness of the StorageResourceId and creationTime in the face of clearItemInfo()
   * calls.
   */
  private void validateBasicInteractions(StorageResourceId resourceId,
      GoogleCloudStorageItemInfo itemInfo, long creationTime, long lastUpdatedTime,
      CacheEntry entry) {
    assertThat(entry.getResourceId()).isEqualTo(resourceId);

    // The 'creationTimeMillis' of the CacheEntry is the time it was constructed, *not* the
    // lastUpdated or timeCreated of the GoogleCloudStorageItemInfo within.
    assertThat(entry.getCreationTimeMillis()).isEqualTo(creationTime);
    assertThat(entry.getItemInfoUpdateTimeMillis()).isEqualTo(lastUpdatedTime);
    assertThat(entry.getItemInfo()).isEqualTo(itemInfo);

    // Re-set the itemInfo without changing it.
    long newUpdateTime = lastUpdatedTime + 1010;
    when(mockClock.currentTimeMillis()).thenReturn(newUpdateTime);
    assertThat(lastUpdatedTime != newUpdateTime).isTrue();
    entry.setItemInfo(itemInfo);

    // No change in creationTime.
    assertThat(entry.getCreationTimeMillis()).isEqualTo(creationTime);
    assertThat(entry.getItemInfoUpdateTimeMillis()).isEqualTo(newUpdateTime);
    assertThat(entry.getItemInfo()).isEqualTo(itemInfo);

    // After clearing itemInfo, the StorageResourceId should still remain inside and creationTime
    // doesn't change.
    entry.clearItemInfo();
    assertThat(entry.getItemInfo()).isNull();
    assertThat(entry.getCreationTimeMillis()).isEqualTo(creationTime);
    assertThat(entry.getItemInfoUpdateTimeMillis()).isEqualTo(0);
    assertThat(entry.getResourceId()).isEqualTo(resourceId);
  }

  @Test
  public void testBasicInfoFromBucketResourceId() {
    long constructorTime = 10L;
    when(mockClock.currentTimeMillis()).thenReturn(constructorTime);
    CacheEntry entry = new CacheEntry(bucketResourceId);

    assertThat(entry.getResourceId()).isEqualTo(bucketResourceId);
    assertThat(entry.getCreationTimeMillis()).isEqualTo(constructorTime);
    assertThat(entry.getItemInfoUpdateTimeMillis()).isEqualTo(0);
    assertThat(entry.getItemInfo()).isNull();

    long updateTime = 20L;
    when(mockClock.currentTimeMillis()).thenReturn(updateTime);
    entry.setItemInfo(bucketInfo);

    validateBasicInteractions(bucketResourceId, bucketInfo, constructorTime, updateTime, entry);
  }

  @Test
  public void testBasicInfoFromBucketItemInfo() {
    long constructorTime = 15L;
    when(mockClock.currentTimeMillis()).thenReturn(constructorTime);
    CacheEntry entry = new CacheEntry(bucketInfo);
    validateBasicInteractions(
        bucketResourceId, bucketInfo, constructorTime, constructorTime, entry);
  }

  @Test
  public void testBasicInfoFromObjectResourceId() {
    long constructorTime = 21L;
    when(mockClock.currentTimeMillis()).thenReturn(constructorTime);
    CacheEntry entry = new CacheEntry(objectResourceId);

    assertThat(entry.getResourceId()).isEqualTo(objectResourceId);
    assertThat(entry.getCreationTimeMillis()).isEqualTo(constructorTime);
    assertThat(entry.getItemInfoUpdateTimeMillis()).isEqualTo(0);
    assertThat(entry.getItemInfo()).isNull();

    long updateTime = 31L;
    when(mockClock.currentTimeMillis()).thenReturn(updateTime);
    entry.setItemInfo(objectInfo);

    validateBasicInteractions(objectResourceId, objectInfo, constructorTime, updateTime, entry);
  }

  @Test
  public void testBasicInfoFromObjectItemInfo() {
    long constructorTime = 51L;
    when(mockClock.currentTimeMillis()).thenReturn(constructorTime);
    CacheEntry entry = new CacheEntry(bucketInfo);

    validateBasicInteractions(
        bucketResourceId, bucketInfo, constructorTime, constructorTime, entry);
  }

  @Test
  public void testSetItemInfoThrowsWhenStorageItemInfoIsNull() {
    CacheEntry entry = new CacheEntry(bucketInfo);
    assertThrows(IllegalArgumentException.class, () -> entry.setItemInfo(null));
  }

  @Test
  public void testSetItemInfoThrowsWhenStorageItemInfoIsRoot() {
    CacheEntry entry = new CacheEntry(bucketInfo);
    assertThrows(
        IllegalArgumentException.class,
        () -> entry.setItemInfo(GoogleCloudStorageItemInfo.ROOT_INFO));
  }

  @Test
  public void testSetItemInfoThrowsWhenStorageItemInfoIsNotFound() {
    CacheEntry entry = new CacheEntry(bucketInfo);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            entry.setItemInfo(GoogleCloudStorageImpl.createItemInfoForNotFound(bucketResourceId)));
  }

  @Test
  public void testSetItemInfoThrowsWhenChangingResourceIds() {
    CacheEntry entry = new CacheEntry(bucketInfo);
    assertThrows(IllegalArgumentException.class, () -> entry.setItemInfo(objectInfo));
  }
}

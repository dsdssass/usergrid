/*******************************************************************************
 * Copyright 2012 Apigee Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.usergrid.persistence;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.usergrid.utils.MapUtils.hashMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.usergrid.persistence.Results.Level;
import org.usergrid.persistence.entities.User;
import org.usergrid.persistence.exceptions.NoIndexException;
import org.usergrid.persistence.exceptions.QueryParseException;
import org.usergrid.utils.JsonUtils;
import org.usergrid.utils.UUIDUtils;

public class CollectionTest extends AbstractPersistenceTest {

  private static final Logger logger = LoggerFactory.getLogger(CollectionTest.class);

  @Test
  public void testCollection() throws Exception {
    UUID applicationId = createApplication("testOrganization", "testCollection");
    assertNotNull(applicationId);

    EntityManager em = emf.getEntityManager(applicationId);
    assertNotNull(em);

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("username", "edanuff");
    properties.put("email", "ed@anuff.com");

    Entity user = em.create("user", properties);
    assertNotNull(user);

    properties = new LinkedHashMap<String, Object>();
    properties.put("actor", new LinkedHashMap<String, Object>() {
      {
        put("displayName", "Ed Anuff");
        put("objectType", "person");
      }
    });
    properties.put("verb", "tweet");
    properties.put("content", "I ate a sammich");
    properties.put("ordinal", 3);

    Entity activity = em.create("activity", properties);
    assertNotNull(activity);

    logger.info("" + activity.getClass());
    logger.info(JsonUtils.mapToFormattedJsonString(activity));

    activity = em.get(activity.getUuid());

    logger.info("" + activity.getClass());
    logger.info(JsonUtils.mapToFormattedJsonString(activity));

    em.addToCollection(user, "activities", activity);

    // test queries on the collection

    properties = new LinkedHashMap<String, Object>();
    properties.put("actor", new LinkedHashMap<String, Object>() {
      {
        put("displayName", "Ed Anuff");
        put("objectType", "person");
      }
    });
    properties.put("verb", "tweet2");
    properties.put("content", "I ate a pickle");
    properties.put("ordinal", 2);
    Entity activity2 = em.create("activity", properties);
    activity2 = em.get(activity2.getUuid());
    em.addToCollection(user, "activities", activity2);

    properties = new LinkedHashMap<String, Object>();
    properties.put("actor", new LinkedHashMap<String, Object>() {
      {
        put("displayName", "Ed Anuff");
        put("objectType", "person");
      }
    });
    properties.put("verb", "tweet2");
    properties.put("content", "I ate an apple");
    properties.put("ordinal", 1);
    Entity activity3 = em.create("activity", properties);
    activity3 = em.get(activity3.getUuid());
    em.addToCollection(user, "activities", activity3);

    // empty query
    Query query = new Query();
    Results r = em.searchCollection(user, "activities", query);
    assertEquals(3, r.size()); // success

    // query verb
    query = new Query().addEqualityFilter("verb", "tweet2");
    r = em.searchCollection(user, "activities", query);
    assertEquals(2, r.size());

    // query verb, sort created
    query = new Query().addEqualityFilter("verb", "tweet2");
    query.addSort("created");
    r = em.searchCollection(user, "activities", query);
    assertEquals(2, r.size());
    List<Entity> entities = r.getEntities();
    assertEquals(entities.get(0).getUuid(), activity2.getUuid());
    assertEquals(entities.get(1).getUuid(), activity3.getUuid());

    // query verb, sort ordinal
    query = new Query().addEqualityFilter("verb", "tweet2");
    query.addSort("ordinal");
    r = em.searchCollection(user, "activities", query);
    assertEquals(2, r.size());
    entities = r.getEntities();
    assertEquals(entities.get(0).getUuid(), activity3.getUuid());
    assertEquals(entities.get(1).getUuid(), activity2.getUuid());

    // empty query, sort content
    query = new Query();
    query.addSort("content");
    r = em.searchCollection(user, "activities", query);
    assertEquals(3, r.size());
    entities = r.getEntities();
    logger.info(JsonUtils.mapToFormattedJsonString(entities));
    assertEquals(entities.get(0).getUuid(), activity2.getUuid());
    assertEquals(entities.get(1).getUuid(), activity.getUuid());
    assertEquals(entities.get(2).getUuid(), activity3.getUuid());

    // empty query, sort verb
    query = new Query();
    query.addSort("verb");
    r = em.searchCollection(user, "activities", query);
    assertEquals(3, r.size());

    // empty query, sort ordinal
    query = new Query();
    query.addSort("ordinal");
    r = em.searchCollection(user, "activities", query);
    assertEquals(3, r.size());
    entities = r.getEntities();
    assertEquals(entities.get(0).getUuid(), activity3.getUuid());
    assertEquals(entities.get(1).getUuid(), activity2.getUuid());
    assertEquals(entities.get(2).getUuid(), activity.getUuid());

    // query ordinal
    query = new Query().addEqualityFilter("ordinal", 2);
    r = em.searchCollection(user, "activities", query);
    assertEquals(1, r.size());

    // query ordinal and sort ordinal
    query = new Query().addEqualityFilter("ordinal", 2);
    query.addSort("ordinal");
    r = em.searchCollection(user, "activities", query);
    assertEquals(1, r.size());
  }

  @Test
  public void userFirstNameSearch() throws Exception {
    UUID applicationId = createApplication("testOrganization", "testFirstName");
    assertNotNull(applicationId);

    EntityManager em = emf.getEntityManager(applicationId);
    assertNotNull(em);

    String firstName = "firstName" + UUIDUtils.newTimeUUID();

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("username", "edanuff");
    properties.put("email", "ed@anuff.com");
    properties.put("firstname", firstName);

    Entity user = em.create("user", properties);
    assertNotNull(user);

    // EntityRef
    Query query = new Query();
    query.addEqualityFilter("firstname", firstName);

    Results r = em.searchCollection(em.getApplicationRef(), "users", query);

    assertTrue(r.size() > 0);

    Entity returned = r.getEntities().get(0);

    assertEquals(user.getUuid(), returned.getUuid());

    // update the username
    String newFirstName = "firstName" + UUIDUtils.newTimeUUID();

    user.setProperty("firstname", newFirstName);

    em.update(user);

    // search with the old username, should be no results
    query = new Query();
    query.addEqualityFilter("firstname", firstName);

    r = em.searchCollection(em.getApplicationRef(), "users", query);

    assertEquals(0, r.size());

    // search with the new username, should be results.

    query = new Query();
    query.addEqualityFilter("firstname", newFirstName);

    r = em.searchCollection(em.getApplicationRef(), "users", query);

    assertTrue(r.size() > 0);

    returned = r.getEntities().get(0);

    assertEquals(user.getUuid(), returned.getUuid());

  }

  @Test
  public void userMiddleNameSearch() throws Exception {
    UUID applicationId = createApplication("testOrganization", "testMiddleName");
    assertNotNull(applicationId);

    EntityManager em = emf.getEntityManager(applicationId);
    assertNotNull(em);

    String middleName = "middleName" + UUIDUtils.newTimeUUID();

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("username", "edanuff");
    properties.put("email", "ed@anuff.com");
    properties.put("middlename", middleName);

    Entity user = em.create("user", properties);
    assertNotNull(user);

    // EntityRef
    Query query = new Query();
    query.addEqualityFilter("middlename", middleName);

    Results r = em.searchCollection(em.getApplicationRef(), "users", query);

    assertTrue(r.size() > 0);

    Entity returned = r.getEntities().get(0);

    assertEquals(user.getUuid(), returned.getUuid());

  }

  @Test
  public void userLastNameSearch() throws Exception {
    UUID applicationId = createApplication("testOrganization", "testLastName");
    assertNotNull(applicationId);

    EntityManager em = emf.getEntityManager(applicationId);
    assertNotNull(em);

    String lastName = "lastName" + UUIDUtils.newTimeUUID();

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("username", "edanuff");
    properties.put("email", "ed@anuff.com");
    properties.put("lastname", lastName);

    Entity user = em.create("user", properties);
    assertNotNull(user);

    // EntityRef
    Query query = new Query();
    query.addEqualityFilter("lastname", lastName);

    Results r = em.searchCollection(em.getApplicationRef(), "users", query);

    assertTrue(r.size() > 0);

    Entity returned = r.getEntities().get(0);

    assertEquals(user.getUuid(), returned.getUuid());

  }

  @Test
  public void testGroups() throws Exception {
    UUID applicationId = createApplication("testOrganization", "testGroups");
    assertNotNull(applicationId);

    EntityManager em = emf.getEntityManager(applicationId);
    assertNotNull(em);

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("username", "edanuff");
    properties.put("email", "ed@anuff.com");

    Entity user1 = em.create("user", properties);
    assertNotNull(user1);

    properties = new LinkedHashMap<String, Object>();
    properties.put("username", "djacobs");
    properties.put("email", "djacobs@gmail.com");

    Entity user2 = em.create("user", properties);
    assertNotNull(user2);

    properties = new LinkedHashMap<String, Object>();
    properties.put("path", "group1");
    Entity group = em.create("group", properties);
    assertNotNull(group);

    em.addToCollection(group, "users", user1);
    em.addToCollection(group, "users", user2);

    properties = new LinkedHashMap<String, Object>();
    properties.put("nickname", "ed");
    em.updateProperties(new SimpleCollectionRef(group, "users", user1), properties);

    Results r = em.searchCollection(group, "users", new Query().addEqualityFilter("member.nickname", "ed")
        .withResultsLevel(Results.Level.LINKED_PROPERTIES));
    logger.info(JsonUtils.mapToFormattedJsonString(r.getEntities()));
    assertEquals(1, r.size());

    em.removeFromCollection(user1, "groups", group);

  }

  @Test
  public void groupNameSearch() throws Exception {
    UUID applicationId = createApplication("testOrganization", "groupNameSearch");
    assertNotNull(applicationId);

    EntityManager em = emf.getEntityManager(applicationId);
    assertNotNull(em);

    String groupName = "groupName" + UUIDUtils.newTimeUUID();

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("title", "testTitle");
    properties.put("path", "testPath");
    properties.put("name", groupName);

    Entity group = em.create("group", properties);
    assertNotNull(group);

    // EntityRef
    Query query = new Query();
    query.addEqualityFilter("name", groupName);

    Results r = em.searchCollection(em.getApplicationRef(), "groups", query);

    assertTrue(r.size() > 0);

    Entity returned = r.getEntities().get(0);

    assertEquals(group.getUuid(), returned.getUuid());

  }

  @Test
  public void groupTitleSearch() throws Exception {
    UUID applicationId = createApplication("testOrganization", "groupTitleSearch");
    assertNotNull(applicationId);

    EntityManager em = emf.getEntityManager(applicationId);
    assertNotNull(em);

    String titleName = "groupName" + UUIDUtils.newTimeUUID();

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("title", titleName);
    properties.put("path", "testPath");
    properties.put("name", "testName");

    Entity group = em.create("group", properties);
    assertNotNull(group);

    // EntityRef
    Query query = new Query();
    query.addEqualityFilter("title", titleName);

    Results r = em.searchCollection(em.getApplicationRef(), "groups", query);

    assertTrue(r.size() > 0);

    Entity returned = r.getEntities().get(0);

    assertEquals(group.getUuid(), returned.getUuid());

  }

  @Test
  public void testSubkeys() throws Exception {

    UUID applicationId = createApplication("testOrganization", "testSubkeys");
    assertNotNull(applicationId);

    EntityManager em = emf.getEntityManager(applicationId);
    assertNotNull(em);

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("username", "edanuff");
    properties.put("email", "ed@anuff.com");

    Entity user = em.create("user", properties);
    assertNotNull(user);

    properties = new LinkedHashMap<String, Object>();
    properties.put("actor", hashMap("displayName", "Ed Anuff").map("objectType", "person"));
    properties.put("verb", "tweet");
    properties.put("content", "I ate a sammich");

    em.addToCollection(user, "activities", em.create("activity", properties));

    properties = new LinkedHashMap<String, Object>();
    properties.put("actor", hashMap("displayName", "Ed Anuff").map("objectType", "person"));
    properties.put("verb", "post");
    properties.put("content", "I wrote a blog post");

    em.addToCollection(user, "activities", em.create("activity", properties));

    properties = new LinkedHashMap<String, Object>();
    properties.put("actor", hashMap("displayName", "Ed Anuff").map("objectType", "person"));
    properties.put("verb", "tweet");
    properties.put("content", "I ate another sammich");

    em.addToCollection(user, "activities", em.create("activity", properties));

    properties = new LinkedHashMap<String, Object>();
    properties.put("actor", hashMap("displayName", "Ed Anuff").map("objectType", "person"));
    properties.put("verb", "post");
    properties.put("content", "I wrote another blog post");

    em.addToCollection(user, "activities", em.create("activity", properties));

    Results r = em.searchCollection(user, "activities", Query.searchForProperty("verb", "post"));
    logger.info(JsonUtils.mapToFormattedJsonString(r.getEntities()));
    assertEquals(2, r.size());

  }

  @Test
  public void emptyQuery() throws Exception {
    UUID applicationId = createApplication("testOrganization", "testEmptyQuery");
    assertNotNull(applicationId);

    EntityManager em = emf.getEntityManager(applicationId);
    assertNotNull(em);

    String firstName = "firstName" + UUIDUtils.newTimeUUID();

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("username", "edanuff");
    properties.put("email", "ed@anuff.com");
    properties.put("firstname", firstName);

    Entity user = em.create("user", properties);
    assertNotNull(user);

    properties = new LinkedHashMap<String, Object>();
    properties.put("username", "djacobs");
    properties.put("email", "djacobs@gmail.com");

    Entity user2 = em.create("user", properties);
    assertNotNull(user2);

    // EntityRef
    Query query = new Query();

    Results r = em.searchCollection(em.getApplicationRef(), "users", query);

    assertEquals(2, r.size());

    Entity returned = r.getEntities().get(0);

    assertEquals(user.getUuid(), returned.getUuid());

    returned = r.getEntities().get(1);

    assertEquals(user2.getUuid(), returned.getUuid());

  }

  @Test
  public void emptyQueryReverse() throws Exception {
    UUID applicationId = createApplication("testOrganization", "testEmptyQueryReverse");
    assertNotNull(applicationId);

    EntityManager em = emf.getEntityManager(applicationId);
    assertNotNull(em);

    String firstName = "firstName" + UUIDUtils.newTimeUUID();

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("username", "edanuff");
    properties.put("email", "ed@anuff.com");
    properties.put("firstname", firstName);

    Entity user = em.create("user", properties);
    assertNotNull(user);

    properties = new LinkedHashMap<String, Object>();
    properties.put("username", "djacobs");
    properties.put("email", "djacobs@gmail.com");

    Entity user2 = em.create("user", properties);
    assertNotNull(user2);

    // EntityRef
    Query query = new Query();
    query.setReversed(true);

    Results r = em.searchCollection(em.getApplicationRef(), "users", query);

    assertEquals(2, r.size());

    Entity returned = r.getEntities().get(0);

    assertEquals(user2.getUuid(), returned.getUuid());

    returned = r.getEntities().get(1);

    assertEquals(user.getUuid(), returned.getUuid());

  }

  @Test
  public void orQuery() throws Exception {
    UUID applicationId = createApplication("testOrganization", "orQuery");
    assertNotNull(applicationId);

    EntityManager em = emf.getEntityManager(applicationId);
    assertNotNull(em);

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("keywords", "blah,test,game");
    properties.put("title", "Solitaire");

    Entity game1 = em.create("orquerygame", properties);
    assertNotNull(game1);

    properties = new LinkedHashMap<String, Object>();
    properties.put("keywords", "random,test");
    properties.put("title", "Hearts");

    Entity game2 = em.create("orquerygame", properties);
    assertNotNull(game2);

    // EntityRef
    Query query = Query.fromQL("select * where keywords contains 'Random' OR keywords contains 'Game'");

    Results r = em.searchCollection(em.getApplicationRef(), "orquerygames", query);

    assertEquals(2, r.size());

    Entity returned = r.getEntities().get(0);

    assertEquals(game2.getUuid(), returned.getUuid());

    returned = r.getEntities().get(1);

    assertEquals(game1.getUuid(), returned.getUuid());

    query = Query.fromQL("select * where( keywords contains 'Random' OR keywords contains 'Game')");

    r = em.searchCollection(em.getApplicationRef(), "orquerygames", query);

    assertEquals(2, r.size());

    returned = r.getEntities().get(0);

    assertEquals(game2.getUuid(), returned.getUuid());

    returned = r.getEntities().get(1);

    assertEquals(game1.getUuid(), returned.getUuid());

    // field order shouldn't matter USERGRID-375
    query = Query.fromQL("select * where keywords contains 'blah' OR title contains 'blah'");

    r = em.searchCollection(em.getApplicationRef(), "orquerygames", query);

    assertEquals(1, r.size());

    returned = r.getEntities().get(0);

    assertEquals(game1.getUuid(), returned.getUuid());

    query = Query.fromQL("select * where  title contains 'blah' OR keywords contains 'blah'");

    r = em.searchCollection(em.getApplicationRef(), "orquerygames", query);

    assertEquals(1, r.size());

    returned = r.getEntities().get(0);

    assertEquals(game1.getUuid(), returned.getUuid());

  }

  @Test
  public void andQuery() throws Exception {
    UUID applicationId = createApplication("testOrganization", "andQuery");
    assertNotNull(applicationId);

    EntityManager em = emf.getEntityManager(applicationId);
    assertNotNull(em);

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("keywords", "blah,test,game");
    properties.put("title", "Solitaire");

    Entity game1 = em.create("game", properties);
    assertNotNull(game1);

    properties = new LinkedHashMap<String, Object>();
    properties.put("keywords", "random,test");
    properties.put("title", "Hearts");

    Entity game2 = em.create("game", properties);
    assertNotNull(game2);

    // overlap
    Query query = Query.fromQL("select * where keywords contains 'test' AND keywords contains 'random'");
    Results r = em.searchCollection(em.getApplicationRef(), "games", query);
    assertEquals(1, r.size());

    // disjoint
    query = Query.fromQL("select * where keywords contains 'random' AND keywords contains 'blah'");
    r = em.searchCollection(em.getApplicationRef(), "games", query);
    assertEquals(0, r.size());

    // same each side
    query = Query.fromQL("select * where keywords contains 'test' AND keywords contains 'test'");
    r = em.searchCollection(em.getApplicationRef(), "games", query);
    assertEquals(2, r.size());

    Entity returned = r.getEntities().get(0);
    assertEquals(game1.getUuid(), returned.getUuid());

    returned = r.getEntities().get(1);
    assertEquals(game2.getUuid(), returned.getUuid());

    // one side, left
    query = Query.fromQL("select * where keywords contains 'test' AND keywords contains 'foobar'");
    r = em.searchCollection(em.getApplicationRef(), "games", query);
    assertEquals(0, r.size());

    // one side, right
    query = Query.fromQL("select * where keywords contains 'foobar' AND keywords contains 'test'");
    r = em.searchCollection(em.getApplicationRef(), "games", query);
    assertEquals(0, r.size());

  }

  @Test
  public void notQuery() throws Exception {
    UUID applicationId = createApplication("testOrganization", "notQuery");
    assertNotNull(applicationId);

    EntityManager em = emf.getEntityManager(applicationId);
    assertNotNull(em);

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("keywords", "blah,test,game");
    properties.put("title", "Solitaire");

    Entity game1 = em.create("game", properties);
    assertNotNull(game1);

    properties = new LinkedHashMap<String, Object>();
    properties.put("keywords", "random,test");
    properties.put("title", "Hearts");

    Entity game2 = em.create("game", properties);
    assertNotNull(game2);

    // simple not
    Query query = Query.fromQL("select * where NOT keywords contains 'game'");
    Results r = em.searchCollection(em.getApplicationRef(), "games", query);
    assertEquals(1, r.size());

    // full negation in simple
    query = Query.fromQL("select * where NOT keywords contains 'test'");
    r = em.searchCollection(em.getApplicationRef(), "games", query);
    assertEquals(0, r.size());

    // simple subtraction
    query = Query.fromQL("select * where keywords contains 'test' AND NOT keywords contains 'random'");
    r = em.searchCollection(em.getApplicationRef(), "games", query);
    assertEquals(1, r.size());

    // disjoint or
    query = Query.fromQL("select * where keywords contains 'random' OR NOT keywords contains 'blah'");
    r = em.searchCollection(em.getApplicationRef(), "games", query);
    assertEquals(1, r.size());

    // disjoint and
    query = Query.fromQL("select * where keywords contains 'random' AND NOT keywords contains 'blah'");
    r = em.searchCollection(em.getApplicationRef(), "games", query);
    assertEquals(1, r.size());

    // self canceling or
    query = Query.fromQL("select * where keywords contains 'test' AND NOT keywords contains 'test'");
    r = em.searchCollection(em.getApplicationRef(), "games", query);
    assertEquals(0, r.size());

    // select all
    query = Query.fromQL("select * where keywords contains 'test' OR NOT keywords contains 'test'");
    r = em.searchCollection(em.getApplicationRef(), "games", query);
    assertEquals(2, r.size());

    // null right and
    query = Query.fromQL("select * where keywords contains 'test' AND NOT keywords contains 'foobar'");
    r = em.searchCollection(em.getApplicationRef(), "games", query);
    assertEquals(2, r.size());

    // null left and
    query = Query.fromQL("select * where keywords contains 'foobar' AND NOT keywords contains 'test'");
    r = em.searchCollection(em.getApplicationRef(), "games", query);
    assertEquals(0, r.size());
  }

  @Test
  public void testKeywordsOrQuery() throws Exception {
    logger.info("testKeywordsOrQuery");

    UUID applicationId = createApplication("testOrganization", "testKeywordsOrQuery");
    assertNotNull(applicationId);

    EntityManager em = emf.getEntityManager(applicationId);
    assertNotNull(em);

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("title", "Galactians 2");
    properties.put("keywords", "Hot, Space Invaders, Classic");
    em.create("game", properties);

    properties = new LinkedHashMap<String, Object>();
    properties.put("title", "Bunnies Extreme");
    properties.put("keywords", "Hot, New");
    em.create("game", properties);

    properties = new LinkedHashMap<String, Object>();
    properties.put("title", "Hot Shots");
    properties.put("keywords", "Action, New");
    em.create("game", properties);

    Query query = Query.fromQL("select * where keywords contains 'hot' or title contains 'hot'");
    Results r = em.searchCollection(em.getApplicationRef(), "games", query);
    logger.info(JsonUtils.mapToFormattedJsonString(r.getEntities()));
    assertEquals(3, r.size());

  }

  @Test
  public void testKeywordsAndQuery() throws Exception {
    logger.info("testKeywordsOrQuery");

    UUID applicationId = createApplication("testOrganization", "testKeywordsAndQuery");
    assertNotNull(applicationId);

    EntityManager em = emf.getEntityManager(applicationId);
    assertNotNull(em);

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("title", "Galactians 2");
    properties.put("keywords", "Hot, Space Invaders, Classic");
    Entity firstGame = em.create("game", properties);

    properties = new LinkedHashMap<String, Object>();
    properties.put("title", "Bunnies Extreme");
    properties.put("keywords", "Hot, New");
    Entity secondGame = em.create("game", properties);

    properties = new LinkedHashMap<String, Object>();
    properties.put("title", "Hot Shots Extreme");
    properties.put("keywords", "Action, New");
    Entity thirdGame = em.create("game", properties);

    Query query = Query.fromQL("select * where keywords contains 'new' and title contains 'extreme'");
    Results r = em.searchCollection(em.getApplicationRef(), "games", query);
    logger.info(JsonUtils.mapToFormattedJsonString(r.getEntities()));
    assertEquals(2, r.size());

    assertEquals(secondGame.getUuid(), r.getEntities().get(0).getUuid());
    assertEquals(thirdGame.getUuid(), r.getEntities().get(1).getUuid());

  }

  @Test
  public void pagingAfterDelete() throws Exception {

    UUID applicationId = createApplication("testOrganization", "pagingAfterDelete");
    assertNotNull(applicationId);

    EntityManager em = emf.getEntityManager(applicationId);
    assertNotNull(em);

    int size = 20;
    List<UUID> entityIds = new ArrayList<UUID>();

    for (int i = 0; i < size; i++) {
      Map<String, Object> properties = new LinkedHashMap<String, Object>();
      properties.put("name", "object" + i);
      Entity created = em.create("objects", properties);

      entityIds.add(created.getUuid());
    }

    Query query = new Query();
    query.setLimit(50);

    Results r = em.searchCollection(em.getApplicationRef(), "objects", query);

    logger.info(JsonUtils.mapToFormattedJsonString(r.getEntities()));

    assertEquals(size, r.size());

    // check they're all the same before deletion
    for (int i = 0; i < size; i++) {
      assertEquals(entityIds.get(i), r.getEntities().get(i).getUuid());
    }

    // now delete 5 items that will span the 10 pages
    for (int i = 5; i < 10; i++) {
      Entity entity = r.getEntities().get(i);
      em.delete(entity);
      entityIds.remove(entity.getUuid());
    }

    // now query with paging
    query = new Query();

    r = em.searchCollection(em.getApplicationRef(), "objects", query);

    assertEquals(10, r.size());

    for (int i = 0; i < 10; i++) {
      assertEquals(entityIds.get(i), r.getEntities().get(i).getUuid());
    }

    // try the next page, set our cursor, it should be the last 5 entities
    query = new Query();
    query.setCursor(r.getCursor());

    r = em.searchCollection(em.getApplicationRef(), "objects", query);

    assertEquals(5, r.size());
    for (int i = 10; i < 15; i++) {
      assertEquals(entityIds.get(i), r.getEntities().get(i - 10).getUuid());
    }

  }

  @Test
  public void pagingLessThanWithCriteria() throws Exception {

    UUID applicationId = createApplication("testOrganization", "pagingLessThanWithCriteria");
    assertNotNull(applicationId);

    EntityManager em = emf.getEntityManager(applicationId);
    assertNotNull(em);

    int size = 40;
    List<UUID> entityIds = new ArrayList<UUID>();

    for (int i = 0; i < size; i++) {
      Map<String, Object> properties = new LinkedHashMap<String, Object>();
      properties.put("index", i);
      Entity created = em.create("page", properties);

      entityIds.add(created.getUuid());
    }

    int pageSize = 10;

    Query query = new Query();
    query.setLimit(pageSize);
    query.addFilter("index < " + size * 2);

    Results r = null;

    // check they're all the same before deletion
    for (int i = 0; i < size / pageSize; i++) {

      r = em.searchCollection(em.getApplicationRef(), "pages", query);

      logger.info(JsonUtils.mapToFormattedJsonString(r.getEntities()));

      assertEquals(pageSize, r.size());

      for (int j = 0; j < pageSize; j++) {
        assertEquals(entityIds.get(i * pageSize + j), r.getEntities().get(j).getUuid());
      }

      query.setCursor(r.getCursor());
    }
    
    //check our last search
    r = em.searchCollection(em.getApplicationRef(), "pages", query);

    assertEquals(0, r.size());
    
    assertNull(r.getCursor());

  }

  @Test
  public void pagingGreaterThanWithCriteria() throws Exception {

    UUID applicationId = createApplication("testOrganization", "pagingGreaterThanWithCriteria");
    assertNotNull(applicationId);

    EntityManager em = emf.getEntityManager(applicationId);
    assertNotNull(em);

    int size = 40;
    List<UUID> entityIds = new ArrayList<UUID>();

    for (int i = 0; i < size; i++) {
      Map<String, Object> properties = new LinkedHashMap<String, Object>();
      properties.put("index", i);
      Entity created = em.create("page", properties);

      entityIds.add(created.getUuid());
    }

    int pageSize = 10;

    Query query = new Query();
    query.setLimit(pageSize);
    query.addFilter("index >= " + size / 2);

    Results r = null;

    // check they're all the same before deletion
    for (int i = 2; i < size / pageSize; i++) {

      r = em.searchCollection(em.getApplicationRef(), "pages", query);

      logger.info(JsonUtils.mapToFormattedJsonString(r.getEntities()));

      assertEquals(pageSize, r.size());

      for (int j = 0; j < pageSize; j++) {
        assertEquals(entityIds.get(i * pageSize + j), r.getEntities().get(j).getUuid());
      }

      query.setCursor(r.getCursor());
    }

    r = em.searchCollection(em.getApplicationRef(), "pages", query);
    
    assertEquals(0, r.size());
    
    assertNull(r.getCursor());

  }

  @Test
  public void pagingWithBoundsCriteria() throws Exception {

    UUID applicationId = createApplication("testOrganization", "pagingWithBoundsCriteria");
    assertNotNull(applicationId);

    EntityManager em = emf.getEntityManager(applicationId);
    assertNotNull(em);

    int size = 40;
    List<UUID> entityIds = new ArrayList<UUID>();

    for (int i = 0; i < size; i++) {
      Map<String, Object> properties = new LinkedHashMap<String, Object>();
      properties.put("index", i);
      Entity created = em.create("page", properties);

      entityIds.add(created.getUuid());
    }

    int pageSize = 10;

    Query query = new Query();
    query.setLimit(pageSize);
    query.addFilter("index >= 10");
    query.addFilter("index <= 29");

    Results r = null;

    // check they're all the same before deletion
    for (int i = 1; i < 3; i++) {

      r = em.searchCollection(em.getApplicationRef(), "pages", query);

      logger.info(JsonUtils.mapToFormattedJsonString(r.getEntities()));

      assertEquals(pageSize, r.size());

      for (int j = 0; j < pageSize; j++) {
        assertEquals(entityIds.get(i * pageSize + j), r.getEntities().get(j).getUuid());
      }

      query.setCursor(r.getCursor());
    }
    
    r = em.searchCollection(em.getApplicationRef(), "pages", query);

    assertEquals(0, r.size());
    
    assertNull(r.getCursor());

  }

  @Test
  public void subpropertyQuerying() throws Exception {
    Map<String, Object> root = new HashMap<String, Object>();

    Map<String, Object> subEntity = new HashMap<String, Object>();

    root.put("rootprop1", "simpleprop");

    subEntity.put("intprop", 10);
    subEntity.put("substring", "I'm a tokenized string that should be indexed");

    root.put("subentity", subEntity);

    UUID applicationId = createApplication("testOrganization", "subpropertyQuerying");
    assertNotNull(applicationId);

    EntityManager em = emf.getEntityManager(applicationId);
    assertNotNull(em);

    Entity saved = em.create("test", root);

    Query query = new Query();
    query.addEqualityFilter("rootprop1", "simpleprop");

    Results results = em.searchCollection(em.getApplicationRef(), "tests", query);

    Entity entity = results.getEntitiesMap().get(saved.getUuid());

    assertNotNull(entity);

    // query on the nested int value
    query = new Query();
    query.addEqualityFilter("subentity.intprop", 10);

    results = em.searchCollection(em.getApplicationRef(), "tests", query);

    entity = results.getEntitiesMap().get(saved.getUuid());

    assertNotNull(entity);

    // query on the nexted tokenized value
    query = new Query();
    query.addContainsFilter("subentity.substring", "tokenized");
    query.addContainsFilter("subentity.substring", "indexed");

    results = em.searchCollection(em.getApplicationRef(), "tests", query);

    entity = results.getEntitiesMap().get(saved.getUuid());

    assertNotNull(entity);
  }

  @Test
  public void arrayQuerying() throws Exception {

    Map<String, Object> root = new HashMap<String, Object>();

    root.put("intprop", 10);
    root.put("array", new String[] { "val1", "val2", "val3 with spaces" });

    Map<String, Object> jsonData = (Map<String, Object>) JsonUtils.parse(JsonUtils.mapToJsonString(root));

    UUID applicationId = createApplication("testOrganization", "arrayQuerying");
    assertNotNull(applicationId);

    EntityManager em = emf.getEntityManager(applicationId);
    assertNotNull(em);

    Entity saved = em.create("test", jsonData);

    Query query = new Query();
    query.addEqualityFilter("intprop", 10);

    Results results = em.searchCollection(em.getApplicationRef(), "tests", query);

    Entity entity = results.getEntitiesMap().get(saved.getUuid());

    assertNotNull(entity);

    // query on the nested int value
    query = new Query();
    query.addEqualityFilter("array", "val1");

    results = em.searchCollection(em.getApplicationRef(), "tests", query);

    entity = results.getEntitiesMap().get(saved.getUuid());

    assertNotNull(entity);

    // query on the nexted tokenized value
    query = new Query();
    query.addEqualityFilter("array", "val2");

    results = em.searchCollection(em.getApplicationRef(), "tests", query);

    entity = results.getEntitiesMap().get(saved.getUuid());

    assertNotNull(entity);

    query = new Query();
    query.addEqualityFilter("array", "val3");

    results = em.searchCollection(em.getApplicationRef(), "tests", query);

    entity = results.getEntitiesMap().get(saved.getUuid());

    assertNull(entity);

    query = new Query();
    query.addContainsFilter("array", "spaces");
    results = em.searchCollection(em.getApplicationRef(), "tests", query);

    entity = results.getEntitiesMap().get(saved.getUuid());

    assertNotNull(entity);

  }

  @Test
  public void stringWithSpaces() throws Exception {
    Map<String, Object> props = new HashMap<String, Object>();

    props.put("myString", "My simple string");

    UUID applicationId = createApplication("testOrganization", "stringWithSpaces");
    assertNotNull(applicationId);

    EntityManager em = emf.getEntityManager(applicationId);
    assertNotNull(em);

    Entity saved = em.create("test", props);

    Query query = new Query();
    query.addEqualityFilter("myString", "My simple string");

    Results results = em.searchCollection(em.getApplicationRef(), "tests", query);

    Entity entity = results.getEntitiesMap().get(saved.getUuid());

    assertNotNull(entity);

  }

  @Test
  public void testSelectTerms() throws Exception {

    UUID applicationId = createApplication("testOrganization", "testSelectTerms");

    EntityManager em = emf.getEntityManager(applicationId);

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("username", "edanuff");
    properties.put("email", "ed@anuff.com");

    em.create("user", properties);

    String s = "select username, email where username = 'edanuff'";
    Query query = Query.fromQL(s);

    Results r = em.searchCollection(em.getApplicationRef(), "users", query);
    assertTrue(r.size() == 1);

    // selection results should be a list of lists
    List<Object> sr = query.getSelectionResults(r);
    assertTrue(sr.size() == 1);

    List firstResult = (List) sr.get(0);
    assertTrue("edanuff".equals(firstResult.get(0)));
    assertTrue("ed@anuff.com".equals(firstResult.get(1)));

  }

  @Test
  public void testRedefineTerms() throws Exception {

    UUID applicationId = createApplication("testOrganization", "testRedefineTerms");

    EntityManager em = emf.getEntityManager(applicationId);

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("username", "edanuff");
    properties.put("email", "ed@anuff.com");

    em.create("user", properties);

    String s = "select {name: username, email: email} where username = 'edanuff'";
    Query query = Query.fromQL(s);

    Results r = em.searchCollection(em.getApplicationRef(), "users", query);
    assertTrue(r.size() == 1);

    // selection results should be a list of lists
    List<Object> sr = query.getSelectionResults(r);
    assertTrue(sr.size() == 1);

    Map firstResult = (Map) sr.get(0);
    assertTrue("edanuff".equals(firstResult.get("name")));
    assertTrue("ed@anuff.com".equals(firstResult.get("email")));
  }

  @Test
  public void testSelectEmailViaConnection() throws Exception {

    UUID applicationId = createApplication("testOrganization", "testSelectEmail");

    EntityManager em = emf.getEntityManager(applicationId);

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("username", "ed@anuff.com");
    properties.put("email", "ed@anuff.com");

    em.create("user", properties);

    String s = "select * where username = 'ed@anuff.com'";
    Query query = Query.fromQL(s);

    Results r = em.searchCollection(em.getApplicationRef(), "users", query);
    assertTrue(r.size() == 1);

    // selection results should be a list of lists
    Entity entity = r.getEntity();

    assertTrue("ed@anuff.com".equals(entity.getProperty("username")));
    assertTrue("ed@anuff.com".equals(entity.getProperty("email")));

    // now create a role and connect it
    properties = new LinkedHashMap<String, Object>();
    properties.put("name", "test");

    Entity foo = em.create("foo", properties);

    em.createConnection(foo, "testconnection", entity);

    // now query via the testConnection, this should work

    query = Query.fromQL(s);
    query.setConnectionType("testconnection");
    query.setEntityType("user");

    r = em.searchConnectedEntities(foo, query);

    assertTrue(r.size() == 1);

    // selection results should be a list of lists
    entity = r.getEntity();
    assertTrue("ed@anuff.com".equals(entity.getProperty("username")));
    assertTrue("ed@anuff.com".equals(entity.getProperty("email")));

  }

  @Test
  public void testNotQueryAnd() throws Exception {

    UUID applicationId = createApplication("testOrganization", "testNotQueryAnd");

    EntityManager em = emf.getEntityManager(applicationId);

    Map<String, Object> location = new LinkedHashMap<String, Object>();
    location.put("Place", "24 Westminster Avenue, Venice, CA 90291, USA");
    location.put("Longitude", -118.47425979999998);
    location.put("Latitude", 33.9887663);

    Map<String, Object> recipient = new LinkedHashMap<String, Object>();
    recipient.put("TimeRequested", 1359077878l);
    recipient.put("Username", "fb_536692245");
    recipient.put("Location", location);

    Map<String, Object> properties = new LinkedHashMap<String, Object>();
    properties.put("Flag", "requested");
    properties.put("Recipient", recipient);

    em.create("loveobject", properties);

    location = new LinkedHashMap<String, Object>();
    location.put("Place", "Via Pietro Maroncelli, 48, 62012 Santa Maria Apparente Province of Macerata, Italy");
    location.put("Longitude", 13.693080199999999);
    location.put("Latitude", 43.2985019);

    recipient = new LinkedHashMap<String, Object>();
    recipient.put("TimeRequested", 1359077878l);
    recipient.put("Username", "fb_100000787138041");
    recipient.put("Location", location);

    properties = new LinkedHashMap<String, Object>();
    properties.put("Flag", "requested");
    properties.put("Recipient", recipient);

    em.create("loveobject", properties);

    // String s = "select * where Flag = 'requested'";
    // String s =
    // "select * where Flag = 'requested' and NOT Recipient.Username = 'fb_536692245' order by created asc";
    String s = "select * where Flag = 'requested' and NOT Recipient.Username = 'fb_536692245' order by created asc";
    Query query = Query.fromQL(s);

    Results r = em.searchCollection(em.getApplicationRef(), "loveobjects", query);
    assertTrue(r.size() == 1);

    String username = (String) ((Map) r.getEntities().get(0).getProperty("Recipient")).get("Username");
    // selection results should be a list of lists
    List<Object> sr = query.getSelectionResults(r);
    assertTrue(sr.size() == 1);

    assertEquals("fb_100000787138041", username);
  }
  
  @Test
  public void runtimeTypeCorrect() throws Exception {

    UUID applicationId = createApplication("testOrganization", "runtimeTypeCorrect");
    assertNotNull(applicationId);

    EntityManager em = emf.getEntityManager(applicationId);
    assertNotNull(em);

    int size = 20;
    List<User> createdEntities = new ArrayList<User>();

    for (int i = 0; i < size; i++) {
      User user = new User();
      user.setEmail(String.format("test%d@usergrid.com", i));
      user.setUsername(String.format("test%d", i));
      user.setName(String.format("test%d", i));

      User created = em.create(user);

      createdEntities.add(created);
      
    }

    Results r = em.getCollection(em.getApplicationRef(), "users", null, 50, Level.ALL_PROPERTIES, false);
    
    logger.info(JsonUtils.mapToFormattedJsonString(r.getEntities()));

    assertEquals(size, r.size());

    // check they're all the same before deletion
    for (int i = 0; i < size; i++) {
      assertEquals(createdEntities.get(i).getUuid(), r.getEntities().get(i).getUuid());
      assertTrue(r.getEntities().get(i) instanceof User);
    }

  }




  @Test
  public void badOrderByBadGrammarAsc() throws Exception {
   
    UUID applicationId = createApplication("testOrganization", "badOrderByBadGrammarAsc");
    assertNotNull(applicationId);

    EntityManager em = emf.getEntityManager(applicationId);
    assertNotNull(em);
    
    String s = "select * where name = 'bob' order by asc";

    String error = null;
    String entityType = null;
    String propertyName = null;

    try {
     em.searchCollection(em.getApplicationRef(), "users", Query.fromQL(s));
     fail("I should throw an exception");
    } catch (NoIndexException nie) {
      error = nie.getMessage();
      entityType = nie.getEntityType();
      propertyName = nie.getPropertyName();
    }

    assertEquals("Entity 'user' with property named '' is not indexed.  You cannot use the this field in queries.", error);
    assertEquals("user", entityType);
    assertEquals("", propertyName);

  }

  @Test
  public void badOrderByBadGrammarDesc() throws Exception {
    UUID applicationId = createApplication("testOrganization", "badOrderByBadGrammarDesc");
    assertNotNull(applicationId);

    EntityManager em = emf.getEntityManager(applicationId);
    assertNotNull(em);
    
    String s = "select * where name = 'bob' order by desc";

    String error = null;
    String entityType = null;
    String propertyName = null;


    try {
     em.searchCollection(em.getApplicationRef(), "users", Query.fromQL(s));
     fail("I should throw an exception");
    } catch (NoIndexException nie) {
      error = nie.getMessage();
      entityType = nie.getEntityType();
      propertyName = nie.getPropertyName();
    }

    assertEquals("Entity 'user' with property named '' is not indexed.  You cannot use the this field in queries.", error);
    assertEquals("user", entityType);
    assertEquals("", propertyName);
  }

  
}

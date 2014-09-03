/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.issue.db;

import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.index.query.BoolFilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.OrFilterBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.sonar.api.rule.RuleKey;
import org.sonar.core.component.ComponentDto;
import org.sonar.core.issue.db.IssueDto;
import org.sonar.core.persistence.DbSession;
import org.sonar.core.rule.RuleDto;
import org.sonar.server.component.persistence.ComponentDao;
import org.sonar.server.db.DbClient;
import org.sonar.server.issue.index.IssueDoc;
import org.sonar.server.issue.index.IssueIndex;
import org.sonar.server.platform.Platform;
import org.sonar.server.rule.RuleTesting;
import org.sonar.server.rule.db.RuleDao;
import org.sonar.server.search.BaseIndex;
import org.sonar.server.search.IndexClient;
import org.sonar.server.search.IndexDefinition;
import org.sonar.server.search.SearchClient;
import org.sonar.server.tester.ServerTester;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;

import static com.google.common.collect.Maps.newHashMap;
import static org.fest.assertions.Assertions.assertThat;

public class IssueBackendMediumTest {

  @ClassRule
  public static ServerTester tester = new ServerTester();

  DbClient dbClient;
  IndexClient indexClient;
  Platform platform;
  DbSession dbSession;

  @Before
  public void setUp() throws Exception {
    dbClient = tester.get(DbClient.class);
    indexClient = tester.get(IndexClient.class);
    platform = tester.get(Platform.class);
    dbSession = dbClient.openSession(false);
    tester.clearDbAndIndexes();
  }

  @After
  public void tearDown() throws Exception {
    if (dbSession != null) {
      dbSession.close();
    }
  }

  @Test
  public void insert_and_find_by_key() throws Exception {
    RuleDto rule = RuleTesting.newXooX1();
    tester.get(RuleDao.class).insert(dbSession, rule);

    ComponentDto project = new ComponentDto()
      .setId(1L)
      .setKey("MyProject")
      .setProjectId(1L);
    tester.get(ComponentDao.class).insert(dbSession, project);

    ComponentDto resource = new ComponentDto()
      .setProjectId(1L)
      .setKey("MyComponent")
      .setId(2L);
    tester.get(ComponentDao.class).insert(dbSession, resource);

    IssueDto issue = new IssueDto()
      .setIssueCreationDate(new Date())
      .setIssueUpdateDate(new Date())
      .setRule(rule)
      .setRootComponent(project)
      .setComponent(resource)
      .setStatus("OPEN").setResolution("OPEN")
      .setKee(UUID.randomUUID().toString())
      .setSeverity("MAJOR");
    dbClient.issueDao().insert(dbSession, issue);

    dbSession.commit();

    // Check that Issue is in Index
    assertThat(indexClient.get(IssueIndex.class).countAll()).isEqualTo(1);

    // should find by key
    IssueDoc issueDoc = indexClient.get(IssueIndex.class).getByKey(issue.getKey());
    assertThat(issueDoc).isNotNull();

    // Check all normalized fields
    assertThat(issueDoc.actionPlanKey()).isEqualTo(issue.getActionPlanKey());
    assertThat(issueDoc.assignee()).isEqualTo(issue.getAssignee());
    assertThat(issueDoc.authorLogin()).isEqualTo(issue.getAuthorLogin());
    assertThat(issueDoc.closeDate()).isEqualTo(issue.getIssueCloseDate());
    assertThat(issueDoc.componentKey()).isEqualTo(issue.getComponentKey());
    assertThat(issueDoc.creationDate()).isEqualTo(issue.getCreatedAt());
    assertThat(issueDoc.effortToFix()).isEqualTo(issue.getEffortToFix());
    assertThat(issueDoc.resolution()).isEqualTo(issue.getResolution());
    assertThat(issueDoc.ruleKey()).isEqualTo(RuleKey.of(issue.getRuleRepo(), issue.getRule()));
    assertThat(issueDoc.line()).isEqualTo(issue.getLine());
    assertThat(issueDoc.message()).isEqualTo(issue.getMessage());
    assertThat(issueDoc.reporter()).isEqualTo(issue.getReporter());
    assertThat(issueDoc.key()).isEqualTo(issue.getKey());
    assertThat(issueDoc.updateDate()).isEqualTo(issue.getIssueUpdateDate());
    assertThat(issueDoc.status()).isEqualTo(issue.getStatus());
    assertThat(issueDoc.severity()).isEqualTo(issue.getSeverity());

    // assertThat(issueDoc.attributes()).isEqualTo(issue.getIssueAttributes());
    // assertThat(issueDoc.isNew()).isEqualTo(issue.isN());
    // assertThat(issueDoc.comments()).isEqualTo(issue.());
  }

  @Test
  public void insert_and_find_after_date() throws Exception {

    RuleDto rule = RuleTesting.newXooX1();
    tester.get(RuleDao.class).insert(dbSession, rule);

    ComponentDto project = new ComponentDto()
      .setId(1L)
      .setKey("MyProject")
      .setProjectId(1L);
    tester.get(ComponentDao.class).insert(dbSession, project);

    ComponentDto resource = new ComponentDto()
      .setId(2L)
      .setKey("MyComponent")
      .setProjectId(1L);
    tester.get(ComponentDao.class).insert(dbSession, resource);

    IssueDto issue = new IssueDto().setId(1L)
      .setRuleId(rule.getId())
      .setRootComponentId(project.getId())
      .setRootComponentKey_unit_test_only(project.key())
      .setComponentId(resource.getId())
      .setComponentKey_unit_test_only(resource.key())
      .setStatus("OPEN").setResolution("OPEN")
      .setKee(UUID.randomUUID().toString());
    dbClient.issueDao().insert(dbSession, issue);

    dbSession.commit();
    assertThat(issue.getId()).isNotNull();

    // Find Issues since forever
    Date t0 = new Date(0);
    assertThat(dbClient.issueDao().findAfterDate(dbSession, t0)).hasSize(1);

    // Should not find any new issues
    Date t1 = new Date();
    assertThat(dbClient.issueDao().findAfterDate(dbSession, t1)).hasSize(0);

    // Should synchronise
    tester.clearIndexes();
    assertThat(indexClient.get(IssueIndex.class).countAll()).isEqualTo(0);
    tester.get(Platform.class).executeStartupTasks();
    assertThat(indexClient.get(IssueIndex.class).countAll()).isEqualTo(1);
  }

  @Test
  public void issue_authorization_on_group() throws Exception {
    SearchClient searchClient = tester.get(SearchClient.class);
    createIssuePermissionIndex(searchClient);
    createIssueProjectIndex(searchClient);

    RuleDto rule = RuleTesting.newXooX1();
    tester.get(RuleDao.class).insert(dbSession, rule);

    ComponentDto project1 = addComponent(1L, 1L, "SonarQube :: Server");
    ComponentDto file1 = addComponent(2L, 1L, "IssueAction.java");
    addIssue(rule, project1, file1);
    addIssueAuthorization(searchClient, project1, null, "user");

    ComponentDto project2 = addComponent(10L, 10L, "SonarQube :: Core");
    ComponentDto file2 = addComponent(11L, 10L, "IssueDao.java");
    addIssue(rule, project2, file2);
    addIssueAuthorization(searchClient, project2, null, "reviewer");

    ComponentDto project3 = addComponent(20L, 20L, "SonarQube :: WS");
    ComponentDto file3 = addComponent(21L, 20L, "IssueWS.java");
    addIssue(rule, project3, file3);
    addIssueAuthorization(searchClient, project3, null, "user");
    addIssueAuthorization(searchClient, project3, null, "reviewer");

    dbSession.commit();

    assertThat(searchIssueWithAuthorization(searchClient, "julien", "user", "reviewer").getHits().getTotalHits()).isEqualTo(3); // ok
    assertThat(searchIssueWithAuthorization(searchClient, "julien", "user").getHits().getTotalHits()).isEqualTo(2); // ko -> 1
    assertThat(searchIssueWithAuthorization(searchClient, "julien", "reviewer").getHits().getTotalHits()).isEqualTo(2); // ko -> 1
    assertThat(searchIssueWithAuthorization(searchClient, "julien", "unknown").getHits().getTotalHits()).isEqualTo(0);
  }

  @Test
  public void issue_authorization_on_user() throws Exception {
    SearchClient searchClient = tester.get(SearchClient.class);
    createIssuePermissionIndex(searchClient);
    createIssueProjectIndex(searchClient);

    RuleDto rule = RuleTesting.newXooX1();
    tester.get(RuleDao.class).insert(dbSession, rule);

    ComponentDto project = addComponent(1L, 1L, "SonarQube");
    ComponentDto file = addComponent(2L, 1L, "IssueAction.java");
    addIssue(rule, project, file);
    addIssueAuthorization(searchClient, project, "julien", null);

    dbSession.commit();

    // The issue is visible for user julien
    assertThat(searchIssueWithAuthorization(searchClient, "julien", "user").getHits().getTotalHits()).isEqualTo(1);
    // The issue is not visible for user simon
    assertThat(searchIssueWithAuthorization(searchClient, "simon", "user").getHits().getTotalHits()).isEqualTo(0);

//    Thread.sleep(Integer.MAX_VALUE);
  }

  @Test
  public void issue_authorization_with_a_lot_of_issues() throws Exception {
    SearchClient searchClient = tester.get(SearchClient.class);
    createIssuePermissionIndex(searchClient);
    createIssueProjectIndex(searchClient);

    RuleDto rule = RuleTesting.newXooX1();
    tester.get(RuleDao.class).insert(dbSession, rule);

    int nbProject = 10;
    int nbUser = 5;
    int componentPerProject = 5;

    Long projectId = 1L;
    Long componentId = 1L;
    BulkRequestBuilder bulkRequestBuilder = new BulkRequestBuilder(searchClient);
    for (int p = 0; p < nbProject; p++) {
      ComponentDto project = addComponent(projectId, projectId, "Project-" + projectId.toString());

      addIssueAuthorization(searchClient, bulkRequestBuilder, project, null, "anyone");
      if (p % 2 == 0) {
        addIssueAuthorization(searchClient, bulkRequestBuilder, project, null, "user");
      }
      for (int u = 0; u < nbUser; u++) {
        addIssueAuthorization(searchClient, bulkRequestBuilder, project, "user-" + u, null);
      }
      for (int c = 0; c < componentPerProject; c++) {
        ComponentDto file = addComponent(componentId, projectId, "Component-" + componentId.toString());
        addIssue(rule, project, file);
        componentId++;
      }
      projectId++;

      if (bulkRequestBuilder.numberOfActions() == nbProject) {
        bulkRequestBuilder.get();
        dbSession.commit();
      }
    }
    bulkRequestBuilder.setRefresh(true).get();
    dbSession.commit();

    // All issues are visible by group anyone
    assertThat(searchIssueWithAuthorization(searchClient, "", "anyone").getHits().getTotalHits()).isEqualTo(nbProject * componentPerProject);
    // Half of issues are visible by group user
    assertThat(searchIssueWithAuthorization(searchClient, "", "user").getHits().getTotalHits()).isEqualTo(nbProject * componentPerProject / 2);
    // user-1 should see all issues
    assertThat(searchIssueWithAuthorization(searchClient, "user-1", "").getHits().getTotalHits()).isEqualTo(nbProject * componentPerProject);

//    Thread.sleep(Integer.MAX_VALUE);
  }

  private SearchResponse searchIssueWithAuthorization(SearchClient searchClient, String user, String... groups) {
    BoolFilterBuilder fb = FilterBuilders.boolFilter();

    OrFilterBuilder or = FilterBuilders.orFilter(FilterBuilders.termFilter("user", user));
    for (String group : groups) {
      or.add(FilterBuilders.termFilter("group", group));
    }
    fb.must(FilterBuilders.termFilter("permission", "read"), or).cache(true);
    // fb.must(FilterBuilders.termFilter("permission", "read"), or);

    SearchRequestBuilder request = searchClient.prepareSearch(IndexDefinition.ISSUES.getIndexName()).setTypes(IndexDefinition.ISSUES.getIndexType())
      .setQuery(
        QueryBuilders.filteredQuery(
          QueryBuilders.matchAllQuery(),
          FilterBuilders.hasParentFilter(IndexDefinition.ISSUES_PROJECT.getIndexType(),
            QueryBuilders.hasChildQuery(IndexDefinition.ISSUES_PERMISSION.getIndexType(),
              QueryBuilders.filteredQuery(
                QueryBuilders.matchAllQuery(), fb)
            )
            )
          )
      )
      .setSize(Integer.MAX_VALUE);

    return searchClient.execute(request);
  }

  private ComponentDto addComponent(Long id, Long projectId, String key) {
    ComponentDto project = new ComponentDto()
      .setId(id)
      .setProjectId(projectId)
      .setKey(key);
    tester.get(ComponentDao.class).insert(dbSession, project);
    return project;
  }

  private IssueDto addIssue(RuleDto rule, ComponentDto project, ComponentDto file) {
    IssueDto issue = new IssueDto()
      .setRuleId(rule.getId())
      .setRootComponentKey_unit_test_only(project.key())
      .setRootComponentId(project.getId())
      .setComponentKey_unit_test_only(file.key())
      .setComponentId(file.getId())
      .setStatus("OPEN").setResolution("OPEN")
      .setKee(UUID.randomUUID().toString());
    dbClient.issueDao().insert(dbSession, issue);
    return issue;
  }

  private void addIssueAuthorization(SearchClient searchClient, ComponentDto project, String user, String group) {
    addIssueAuthorization(searchClient, null, project, user, group);
  }

  private void addIssueAuthorization(SearchClient searchClient, BulkRequestBuilder bulkRequestBuilder, ComponentDto project, String user, String group) {
    Map<String, Object> permissionSource = newHashMap();
    permissionSource.put("_parent", project.key());
    permissionSource.put("permission", "read");
    permissionSource.put("project", project.key());
    if (user != null) {
      permissionSource.put("user", user);
    }
    if (group != null) {
      permissionSource.put("group", group);
    }

    IndexRequestBuilder permissionRequestBuilder = searchClient.prepareIndex(IndexDefinition.ISSUES_PERMISSION.getIndexName(), IndexDefinition.ISSUES_PERMISSION.getIndexType())
      .setSource(permissionSource);
    if (bulkRequestBuilder != null) {
      bulkRequestBuilder.add(permissionRequestBuilder);
    } else {
      permissionRequestBuilder.setRefresh(true).get();
    }

    Map<String, Object> projectSource = newHashMap();
    projectSource.put("key", project.key());

    IndexRequestBuilder projectRequestBuilder = searchClient.prepareIndex(IndexDefinition.ISSUES_PROJECT.getIndexName(), IndexDefinition.ISSUES_PROJECT.getIndexType())
      .setId(project.key())
      .setSource(projectSource);
    if (bulkRequestBuilder != null) {
      bulkRequestBuilder.add(projectRequestBuilder);
    } else {
      projectRequestBuilder.setRefresh(true).get();
    }
  }

  private BaseIndex createIssueProjectIndex(final SearchClient searchClient) {
    BaseIndex baseIndex = new BaseIndex(
      IndexDefinition.createFor(IndexDefinition.ISSUES_PROJECT.getIndexName(), IndexDefinition.ISSUES_PROJECT.getIndexType()),
      null, searchClient) {
      @Override
      protected String getKeyValue(Serializable key) {
        return null;
      }

      @Override
      protected org.elasticsearch.common.settings.Settings getIndexSettings() throws IOException {
        return ImmutableSettings.builder().build();
      }

      @Override
      protected Map mapProperties() {
        Map<String, Object> mapping = new HashMap<String, Object>();
        mapping.put("project", mapStringField());
        return mapping;
      }

      protected Map mapStringField() {
        Map<String, Object> mapping = new HashMap<String, Object>();
        mapping.put("type", "string");
        mapping.put("index", "analyzed");
        mapping.put("index_analyzer", "keyword");
        mapping.put("search_analyzer", "whitespace");
        return mapping;
      }

      @Override
      protected Map mapKey() {
        return Collections.emptyMap();
      }

      @Override
      public Object toDoc(Map fields) {
        return null;
      }
    };
    baseIndex.start();
    return baseIndex;
  }

  private BaseIndex createIssuePermissionIndex(final SearchClient searchClient) {
    BaseIndex baseIndex = new BaseIndex(
      IndexDefinition.createFor(IndexDefinition.ISSUES_PERMISSION.getIndexName(), IndexDefinition.ISSUES_PERMISSION.getIndexType()),
      null, searchClient) {
      @Override
      protected String getKeyValue(Serializable key) {
        return null;
      }

      @Override
      protected org.elasticsearch.common.settings.Settings getIndexSettings() throws IOException {
        return ImmutableSettings.builder().build();
      }

      @Override
      protected Map mapDomain() {
        Map<String, Object> mapping = new HashMap<String, Object>();
        mapping.put("dynamic", false);
        mapping.put("_parent", mapParent());
        mapping.put("_routing", mapRouting());
        mapping.put("properties", mapProperties());
        return mapping;
      }

      private Object mapParent() {
        Map<String, Object> mapping = new HashMap<String, Object>();
        mapping.put("type", getParentType());
        return mapping;
      }

      private String getParentType() {
        return IndexDefinition.ISSUES_PROJECT.getIndexType();
      }

      private Map mapRouting() {
        Map<String, Object> mapping = new HashMap<String, Object>();
        mapping.put("required", true);
        mapping.put("path", "project");
        return mapping;
      }

      @Override
      protected Map mapProperties() {
        Map<String, Object> mapping = new HashMap<String, Object>();
        mapping.put("permission", mapStringField());
        mapping.put("project", mapStringField());
        mapping.put("group", mapStringField());
        mapping.put("user", mapStringField());
        return mapping;
      }

      protected Map mapStringField() {
        Map<String, Object> mapping = new HashMap<String, Object>();
        mapping.put("type", "string");
        mapping.put("index", "analyzed");
        mapping.put("index_analyzer", "keyword");
        mapping.put("search_analyzer", "whitespace");
        return mapping;
      }

      @Override
      protected Map mapKey() {
        Map<String, Object> mapping = new HashMap<String, Object>();
        return mapping;
      }

      @Override
      public Object toDoc(Map fields) {
        return null;
      }
    };
    baseIndex.start();
    return baseIndex;
  }
}

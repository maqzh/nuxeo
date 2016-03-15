/*
 * (C) Copyright 2014-2015 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Tiry
 *     Benoit Delbosc
 */
package org.nuxeo.elasticsearch.audit;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.count.CountResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.BoolFilterBuilder;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermFilterBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.metrics.max.Max;
import org.elasticsearch.search.sort.SortOrder;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.nuxeo.common.utils.TextTemplate;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.uidgen.UIDGeneratorService;
import org.nuxeo.ecm.core.uidgen.UIDSequencer;
import org.nuxeo.ecm.core.work.api.Work;
import org.nuxeo.ecm.core.work.api.Work.State;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.platform.audit.api.AuditReader;
import org.nuxeo.ecm.platform.audit.api.ExtendedInfo;
import org.nuxeo.ecm.platform.audit.api.FilterMapEntry;
import org.nuxeo.ecm.platform.audit.api.LogEntry;
import org.nuxeo.ecm.platform.audit.api.query.AuditQueryException;
import org.nuxeo.ecm.platform.audit.api.query.DateRangeParser;
import org.nuxeo.ecm.platform.audit.service.AbstractAuditBackend;
import org.nuxeo.ecm.platform.audit.service.AuditBackend;
import org.nuxeo.ecm.platform.audit.service.BaseLogEntryProvider;
import org.nuxeo.ecm.platform.query.api.PredicateDefinition;
import org.nuxeo.ecm.platform.query.api.PredicateFieldDefinition;
import org.nuxeo.elasticsearch.ElasticSearchConstants;
import org.nuxeo.elasticsearch.api.ElasticSearchAdmin;
import org.nuxeo.elasticsearch.audit.io.AuditEntryJSONReader;
import org.nuxeo.elasticsearch.audit.io.AuditEntryJSONWriter;
import org.nuxeo.runtime.api.Framework;

/**
 * Implementation of the {@link AuditBackend} interface using Elasticsearch persistence
 *
 * @author tiry
 */
public class ESAuditBackend extends AbstractAuditBackend implements AuditBackend {

    public static final String SEQ_NAME = "audit";

    public static final String MIGRATION_FLAG_PROP = "audit.elasticsearch.migration";

    public static final String MIGRATION_BATCH_SIZE_PROP = "audit.elasticsearch.migration.batchSize";

    public static final String MIGRATION_DONE_EVENT = "sqlToElasticsearchMigrationDone";

    public static final int MIGRATION_DEFAULT_BACTH_SIZE = 1000;

    protected Client esClient = null;

    protected static final Log log = LogFactory.getLog(ESAuditBackend.class);

    protected BaseLogEntryProvider provider = null;

    protected Client getClient() {
        if (esClient == null) {
            log.info("Activate Elasticsearch backend for Audit");
            ElasticSearchAdmin esa = Framework.getService(ElasticSearchAdmin.class);
            esClient = esa.getClient();
            ensureUIDSequencer(esClient);
        }
        return esClient;
    }

    protected boolean isMigrationDone() {
        AuditReader reader = Framework.getService(AuditReader.class);
        List<LogEntry> entries = reader.queryLogs(new String[] { MIGRATION_DONE_EVENT }, null);
        return !entries.isEmpty();
    }

    @Override
    public void deactivate() {
        super.deactivate();
        if (esClient != null) {
            try {
                esClient.close();
            } finally {
                esClient = null;
            }
        }
    }

    @Override
    public List<LogEntry> getLogEntriesFor(String uuid, Map<String, FilterMapEntry> filterMap, boolean doDefaultSort) {
        SearchRequestBuilder builder = getSearchRequestBuilder();
        TermFilterBuilder docFilter = FilterBuilders.termFilter("docUUID", uuid);
        FilterBuilder filter;
        if (MapUtils.isEmpty(filterMap)) {
            filter = docFilter;
        } else {
            filter = FilterBuilders.boolFilter();
            ((BoolFilterBuilder) filter).must(docFilter);
            for (String key : filterMap.keySet()) {
                FilterMapEntry entry = filterMap.get(key);
                ((BoolFilterBuilder) filter).must(FilterBuilders.termFilter(entry.getColumnName(), entry.getObject()));
            }
        }
        builder.setQuery(QueryBuilders.constantScoreQuery(filter)).setSize(Integer.MAX_VALUE);
        if (doDefaultSort) {
            builder.addSort("eventDate", SortOrder.DESC);
        }
        logSearchRequest(builder);
        SearchResponse searchResponse = builder.get();
        logSearchResponse(searchResponse);
        return buildLogEntries(searchResponse);
    }

    protected List<LogEntry> buildLogEntries(SearchResponse searchResponse) {
        List<LogEntry> entries = new ArrayList<>(searchResponse.getHits().getHits().length);
        for (SearchHit hit : searchResponse.getHits()) {
            try {
                entries.add(AuditEntryJSONReader.read(hit.getSourceAsString()));
            } catch (IOException e) {
                log.error("Error while reading Audit Entry from ES", e);
            }
        }
        return entries;
    }

    protected SearchRequestBuilder getSearchRequestBuilder() {
        return getClient().prepareSearch(getESIndexName()).setTypes(ElasticSearchConstants.ENTRY_TYPE).setSearchType(
                SearchType.DFS_QUERY_THEN_FETCH);
    }

    @Override
    public LogEntry getLogEntryByID(long id) {
        GetResponse ret = getClient().prepareGet(getESIndexName(), ElasticSearchConstants.ENTRY_TYPE,
                String.valueOf(id)).get();
        if (!ret.isExists()) {
            return null;
        }
        try {
            return AuditEntryJSONReader.read(ret.getSourceAsString());
        } catch (IOException e) {
            throw new RuntimeException("Unable to read Entry for id " + id, e);
        }
    }

    public SearchRequestBuilder buildQuery(String query, Map<String, Object> params) {
        if (params != null && params.size() > 0) {
            query = expandQueryVariables(query, params);
        }
        SearchRequestBuilder builder = getSearchRequestBuilder();
        builder.setQuery(query);
        return builder;
    }

    public String expandQueryVariables(String query, Object[] params) {
        Map<String, Object> qParams = new HashMap<>();
        for (int i = 0; i < params.length; i++) {
            query = query.replaceFirst("\\?", "\\${param" + i + "}");
            qParams.put("param" + i, params[i]);
        }
        return expandQueryVariables(query, qParams);
    }

    public String expandQueryVariables(String query, Map<String, Object> params) {
        if (params != null && params.size() > 0) {
            TextTemplate tmpl = new TextTemplate();
            for (String key : params.keySet()) {
                Object val = params.get(key);
                if (val == null) {
                    continue;
                } else if (val instanceof Calendar) {
                    tmpl.setVariable(key, ISODateTimeFormat.dateTime().print(new DateTime(val)));
                } else if (val instanceof Date) {
                    tmpl.setVariable(key, ISODateTimeFormat.dateTime().print(new DateTime(val)));
                } else {
                    tmpl.setVariable(key, val.toString());
                }
            }
            query = tmpl.processText(query);
        }
        return query;
    }

    @Override
    public List<?> nativeQuery(String query, Map<String, Object> params, int pageNb, int pageSize) {
        SearchRequestBuilder builder = buildQuery(query, params);
        if (pageNb > 0) {
            builder.setFrom(pageNb * pageSize);
        }
        if (pageSize > 0) {
            builder.setSize(pageSize);
        }
        logSearchRequest(builder);
        SearchResponse searchResponse = builder.get();
        logSearchResponse(searchResponse);
        return buildLogEntries(searchResponse);
    }

    @Override
    public List<LogEntry> queryLogsByPage(String[] eventIds, Date limit, String[] categories, String path, int pageNb,
            int pageSize) {
        SearchRequestBuilder builder = getSearchRequestBuilder();
        BoolFilterBuilder filterBuilder = FilterBuilders.boolFilter();
        if (eventIds != null && eventIds.length > 0) {
            if (eventIds.length == 1) {
                filterBuilder.must(FilterBuilders.termFilter("eventId", eventIds[0]));
            } else {
                filterBuilder.must(FilterBuilders.termsFilter("eventId", eventIds));
            }
        }
        if (categories != null && categories.length > 0) {
            if (categories.length == 1) {
                filterBuilder.must(FilterBuilders.termFilter("category", categories[0]));
            } else {
                filterBuilder.must(FilterBuilders.termsFilter("category", categories));
            }
        }
        if (path != null) {
            filterBuilder.must(FilterBuilders.termFilter("docPath", path));
        }

        if (limit != null) {
            filterBuilder.must(FilterBuilders.rangeFilter("eventDate").lt(limit));
        }

        builder.setQuery(QueryBuilders.constantScoreQuery(filterBuilder));

        if (pageNb > 0) {
            builder.setFrom(pageNb * pageSize);
        }
        if (pageSize > 0) {
            builder.setSize(pageSize);
        } else {
            builder.setSize(Integer.MAX_VALUE);
        }
        logSearchRequest(builder);
        SearchResponse searchResponse = builder.get();
        logSearchResponse(searchResponse);
        return buildLogEntries(searchResponse);
    }

    @Override
    public List<LogEntry> queryLogsByPage(String[] eventIds, String dateRange, String[] categories, String path,
            int pageNb, int pageSize) {

        Date limit = null;
        if (dateRange != null) {
            try {
                limit = DateRangeParser.parseDateRangeQuery(new Date(), dateRange);
            } catch (AuditQueryException aqe) {
                aqe.addInfo("Wrong date range query. Query was " + dateRange);
                throw aqe;
            }
        }
        return queryLogsByPage(eventIds, limit, categories, path, pageNb, pageSize);
    }

    @Override
    public void addLogEntries(List<LogEntry> entries) {

        if (entries.isEmpty()) {
            return;
        }

        BulkRequestBuilder bulkRequest = getClient().prepareBulk();
        JsonFactory factory = new JsonFactory();

        UIDGeneratorService uidGeneratorService = Framework.getService(UIDGeneratorService.class);
        UIDSequencer seq = uidGeneratorService.getSequencer();

        try {

            for (LogEntry entry : entries) {
                entry.setId(seq.getNext(SEQ_NAME));
                if (log.isDebugEnabled()) {
                    log.debug(String.format("Indexing log enry Id: %s, with logDate : %s, for docUUID: %s ",
                            entry.getId(), entry.getLogDate(), entry.getDocUUID()));
                }
                XContentBuilder builder = jsonBuilder();
                JsonGenerator jsonGen = factory.createJsonGenerator(builder.stream());
                AuditEntryJSONWriter.asJSON(jsonGen, entry);
                bulkRequest.add(getClient().prepareIndex(getESIndexName(), ElasticSearchConstants.ENTRY_TYPE,
                        String.valueOf(entry.getId())).setSource(builder));
            }

            BulkResponse bulkResponse = bulkRequest.execute().actionGet();
            if (bulkResponse.hasFailures()) {
                for (BulkItemResponse response : bulkResponse.getItems()) {
                    if (response.isFailed()) {
                        log.error("Unable to index audit entry " + response.getItemId() + " :"
                                + response.getFailureMessage());
                    }
                }
            }
        } catch (IOException e) {
            throw new NuxeoException("Error while indexing Audit entries", e);
        }

    }

    @Override
    public Long getEventsCount(String eventId) {
        CountResponse res = getClient().prepareCount(getESIndexName()).setTypes(ElasticSearchConstants.ENTRY_TYPE).setQuery(
                QueryBuilders.constantScoreQuery(FilterBuilders.termFilter("eventId", eventId))).get();
        return res.getCount();
    }

    protected BaseLogEntryProvider getProvider() {

        if (provider == null) {
            provider = new BaseLogEntryProvider() {

                @Override
                public int removeEntries(String eventId, String pathPattern) {
                    throw new UnsupportedOperationException("Not implemented yet!");
                }

                @Override
                public void addLogEntry(LogEntry logEntry) {
                    List<LogEntry> entries = new ArrayList<>();
                    entries.add(logEntry);
                    addLogEntries(entries);
                }
            };
        }
        return provider;
    }

    @Override
    public long syncLogCreationEntries(final String repoId, final String path, final Boolean recurs) {
        return syncLogCreationEntries(getProvider(), repoId, path, recurs);
    }

    protected FilterBuilder buildFilter(PredicateDefinition[] predicates, DocumentModel searchDocumentModel) {

        if (searchDocumentModel == null) {
            return FilterBuilders.matchAllFilter();
        }

        BoolFilterBuilder filterBuilder = FilterBuilders.boolFilter();

        int nbFilters = 0;

        for (PredicateDefinition predicate : predicates) {

            // extract data from DocumentModel
            PredicateFieldDefinition[] fieldDef = predicate.getValues();
            Object[] val = new Object[fieldDef.length];
            for (int fidx = 0; fidx < fieldDef.length; fidx++) {
                if (fieldDef[fidx].getXpath() != null) {
                    val[fidx] = searchDocumentModel.getPropertyValue(fieldDef[fidx].getXpath());
                } else {
                    val[fidx] = searchDocumentModel.getProperty(fieldDef[fidx].getSchema(), fieldDef[fidx].getName());
                }
            }

            if (!isNonNullParam(val)) {
                // skip predicate where all values are null
                continue;
            }

            nbFilters++;

            String op = predicate.getOperator();
            if (op.equalsIgnoreCase("IN")) {

                String[] values = null;
                if (val[0] instanceof Iterable<?>) {
                    List<String> l = new ArrayList<>();
                    Iterable<?> vals = (Iterable<?>) val[0];
                    Iterator<?> valueIterator = vals.iterator();

                    while (valueIterator.hasNext()) {

                        Object v = valueIterator.next();
                        if (v != null) {
                            l.add(v.toString());
                        }
                    }
                    values = l.toArray(new String[l.size()]);
                } else if (val[0] instanceof Object[]) {
                    values = (String[]) val[0];
                }
                filterBuilder.must(FilterBuilders.termsFilter(predicate.getParameter(), values));
            } else if (op.equalsIgnoreCase("BETWEEN")) {
                filterBuilder.must(FilterBuilders.rangeFilter(predicate.getParameter()).gt(val[0]));
                if (val.length > 1) {
                    filterBuilder.must(FilterBuilders.rangeFilter(predicate.getParameter()).lt(val[1]));
                }
            } else if (">".equals(op)) {
                filterBuilder.must(FilterBuilders.rangeFilter(predicate.getParameter()).gt(val[0]));
            } else if (">=".equals(op)) {
                filterBuilder.must(FilterBuilders.rangeFilter(predicate.getParameter()).gte(val[0]));
            } else if ("<".equals(op)) {
                filterBuilder.must(FilterBuilders.rangeFilter(predicate.getParameter()).lt(val[0]));
            } else if ("<=".equals(op)) {
                filterBuilder.must(FilterBuilders.rangeFilter(predicate.getParameter()).lte(val[0]));
            } else {
                filterBuilder.must(FilterBuilders.termFilter(predicate.getParameter(), val[0]));
            }
        }

        if (nbFilters == 0) {
            return FilterBuilders.matchAllFilter();
        }
        return filterBuilder;
    }

    public SearchRequestBuilder buildSearchQuery(String fixedPart, PredicateDefinition[] predicates,
            DocumentModel searchDocumentModel) {
        SearchRequestBuilder builder = getSearchRequestBuilder();
        QueryBuilder queryBuilder = QueryBuilders.wrapperQuery(fixedPart);
        FilterBuilder filterBuilder = buildFilter(predicates, searchDocumentModel);
        builder.setQuery(QueryBuilders.filteredQuery(queryBuilder, filterBuilder));
        return builder;
    }

    protected boolean isNonNullParam(Object[] val) {
        if (val == null) {
            return false;
        }
        for (Object v : val) {
            if (v != null) {
                if (v instanceof String) {
                    if (!((String) v).isEmpty()) {
                        return true;
                    }
                } else if (v instanceof String[]) {
                    if (((String[]) v).length > 0) {
                        return true;
                    }
                } else {
                    return true;
                }
            }
        }
        return false;
    }

    public String migrate(final int batchSize) {

        final String MIGRATION_WORK_ID = "AuditMigration";

        WorkManager wm = Framework.getService(WorkManager.class);
        State migrationState = wm.getWorkState(MIGRATION_WORK_ID);
        if (migrationState != null) {
            return "Migration already scheduled : " + migrationState.toString();
        }

        Work migrationWork = new ESAuditMigrationWork(MIGRATION_WORK_ID, batchSize);
        wm.schedule(migrationWork);
        return "Migration work started : " + MIGRATION_WORK_ID;
    }

    protected void logSearchResponse(SearchResponse response) {
        if (log.isDebugEnabled()) {
            log.debug("Response: " + response.toString());
        }
    }

    protected void logSearchRequest(SearchRequestBuilder request) {
        if (log.isDebugEnabled()) {
            log.debug(String.format("Search query: curl -XGET 'http://localhost:9200/%s/%s/_search?pretty' -d '%s'",
                    getESIndexName(), ElasticSearchConstants.ENTRY_TYPE, request.toString()));
        }
    }

    @Override
    public void onApplicationStarted() {
        super.onApplicationStarted();
        if (Boolean.parseBoolean(Framework.getProperty(MIGRATION_FLAG_PROP))) {
            if (!isMigrationDone()) {
                log.info(String.format(
                        "Property %s is true and migration is not done yet, processing audit migration from SQL to Elasticsearch index",
                        MIGRATION_FLAG_PROP));
                // Drop audit index first in case of a previous bad migration
                ElasticSearchAdmin esa = Framework.getService(ElasticSearchAdmin.class);
                esa.dropAndInitIndex(getESIndexName());
                int batchSize = MIGRATION_DEFAULT_BACTH_SIZE;
                String batchSizeProp = Framework.getProperty(MIGRATION_BATCH_SIZE_PROP);
                if (batchSizeProp != null) {
                    batchSize = Integer.parseInt(batchSizeProp);
                }
                migrate(batchSize);
            } else {
                log.warn(String.format(
                        "Property %s is true but migration is already done, please set this property to false",
                        MIGRATION_FLAG_PROP));
            }
        } else {
            log.debug(String.format("Property %s is false, not processing any migration", MIGRATION_FLAG_PROP));
        }
    }

    /**
     * Ensures the audit sequence returns an UID greater or equal than the maximum log entry id.
     */
    protected void ensureUIDSequencer(Client esClient) {
        boolean auditIndexExists = esClient.admin().indices().prepareExists(getESIndexName()).execute().actionGet().isExists();
        if (!auditIndexExists) {
            return;
        }

        // Get max log entry id
        SearchRequestBuilder builder = getSearchRequestBuilder();
        builder.setQuery(QueryBuilders.matchAllQuery()).addAggregation(AggregationBuilders.max("maxAgg").field("id"));
        SearchResponse searchResponse = builder.execute().actionGet();
        Max agg = searchResponse.getAggregations().get("maxAgg");
        int maxLogEntryId = (int) agg.getValue();

        // Get next sequence id
        UIDGeneratorService uidGeneratorService = Framework.getService(UIDGeneratorService.class);
        UIDSequencer seq = uidGeneratorService.getSequencer();
        int nextSequenceId = seq.getNext(SEQ_NAME);

        // Increment sequence to max log entry id if needed
        if (nextSequenceId < maxLogEntryId) {
            log.info(String.format("Next UID returned by %s sequence is %d, initializing sequence to %d", SEQ_NAME,
                    nextSequenceId, maxLogEntryId));
            seq.initSequence(SEQ_NAME, maxLogEntryId);
        }
    }

    @Override
    public ExtendedInfo newExtendedInfo(Serializable value) {
        return new ESExtendedInfo(value);
    }

    protected String getESIndexName() {
        ElasticSearchAdmin esa = Framework.getService(ElasticSearchAdmin.class);
        return esa.getIndexNameForType(ElasticSearchConstants.ENTRY_TYPE);
    }
}

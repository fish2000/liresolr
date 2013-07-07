package net.semanticmetadata.lire.solr;

import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;

/**
 * This file is part of LIRE, a Java library for content based image retrieval.
 *
 * @author Mathias Lux, mathias@juggle.at, 07.07.13
 */

public class LireRequestHandler extends RequestHandlerBase {
    private int countRequests = 0;

    @Override
    public void init(NamedList args) {
        super.init(args);
    }

    @Override
    public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception {
        SolrParams params = req.getParams();
        if (params.get("request").equals("add")) countRequests++;
        rsp.add("Test-name", "Test-val");
    }

    @Override
    public String getDescription() {
        return "LIRE Request Handler to add images to an index and search them";
    }

    @Override
    public String getSource() {
        return "http://lire-project.net";
    }

    @Override
    public NamedList<Object> getStatistics() {
        // Change stats here to get an insight in the admin console.
        NamedList<Object> statistics = super.getStatistics();
        statistics.add("Number of Requests", countRequests);
        return statistics;
    }
}

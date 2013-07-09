package net.semanticmetadata.lire.solr;

import net.semanticmetadata.lire.DocumentBuilder;
import net.semanticmetadata.lire.imageanalysis.*;
import net.semanticmetadata.lire.impl.GenericDocumentBuilder;
import net.semanticmetadata.lire.impl.SimpleResult;
import org.apache.commons.codec.binary.Base64;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.util.Version;
import org.apache.solr.client.solrj.response.TermsResponse;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.DocIterator;
import org.apache.solr.search.DocList;
import org.apache.solr.search.DocSet;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.search.grouping.distributed.command.QueryCommand;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeSet;

/**
 * This file is part of LIRE, a Java library for content based image retrieval.
 *
 * @author Mathias Lux, mathias@juggle.at, 07.07.13
 */

public class LireRequestHandler extends RequestHandlerBase {
    private int countRequests = 0;
    private static HashMap<String, Class> fieldToClass = new HashMap<String, Class>(5);

    static {
        fieldToClass.put("cl_ha", ColorLayout.class);
        fieldToClass.put("ph_ha", PHOG.class);
        fieldToClass.put("oh_ha", OpponentHistogram.class);
        fieldToClass.put("eh_ha", EdgeHistogram.class);
        fieldToClass.put("jc_ha", JCD.class);
    }

    @Override
    public void init(NamedList args) {
        super.init(args);
    }

    @Override
    public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception {
        SolrParams params = req.getParams();
        SolrIndexSearcher searcher = req.getSearcher();
        // get the params needed:
        // hashes=x y z ...
        // feature=<base64>
        // field=<cl_ha|ph_ha|...>
        String[] hashes = params.get("hashes").trim().split(" ");
        byte[] featureVector = Base64.decodeBase64(params.get("feature"));
        String field = params.get("field");
        int maximumHits = 20;
        if (params.getInt("rows")!=null)
            maximumHits = params.getInt("rows");
        // create boolean query:
        System.out.println("** Creating query.");
        // TODO: This does not work as intended! Problems ahead!
//        QueryParser qp = new QueryParser(Version.LUCENE_43, field, new WhitespaceAnalyzer(Version.LUCENE_43));
        BooleanQuery query = new BooleanQuery();
        for (int i = 0; i < hashes.length; i++) {
            // be aware that the hashFunctionsFileName of the field must match the one you put the hashes in before.
            hashes[i] = hashes[i].trim();
            if (hashes[i].length() > 0) {
                query.add(new BooleanClause(new TermQuery(new Term(field, hashes[i].trim())), BooleanClause.Occur.SHOULD));
                System.out.println("** " + field + ": " + hashes[i].trim() );
            }
        }
        System.out.println("** Doing search.");
        // get results:

        TopDocs docs = searcher.search(query, 500);
        // temp feature
        LireFeature f = (LireFeature) fieldToClass.get(field).newInstance();
        // query feature
        LireFeature q = (LireFeature) fieldToClass.get(field).newInstance();
        q.setByteArrayRepresentation(featureVector);
        System.out.println("** Query feature: " + q.getClass().getName() + ": " + Arrays.toString(q.getDoubleHistogram()));
        System.out.println("** Doing re-rank.");
        // re-rank
        TreeSet<SimpleResult> resultScoreDocs = new TreeSet<SimpleResult>();
        float maxDistance = -1f;
        float tmpScore;

        int count = 0;
        String name = field.replace("_ha", "_hi");
        System.out.println("** Iterating docs.");
        for (int i = 0; i < docs.scoreDocs.length; i++) {
            System.out.println("** " + count);
            System.out.println("** Getting document " + docs.scoreDocs[i].doc + " with score " + docs.scoreDocs[i].score);
            Document d = searcher.doc(docs.scoreDocs[i].doc);
            System.out.println("** Getting data from field " + name);
            f.setByteArrayRepresentation(d.getBinaryValue(name).bytes, d.getBinaryValue(name).offset, d.getBinaryValues(name).length);
            tmpScore = q.getDistance(f);
            assert (tmpScore >= 0);
            if (resultScoreDocs.size() < maximumHits) {
                resultScoreDocs.add(new SimpleResult(tmpScore, d, count));
                maxDistance = Math.max(maxDistance, tmpScore);
            } else if (tmpScore < maxDistance) {
                // if it is nearer to the sample than at least one of the current set:
                // remove the last one ...
                resultScoreDocs.remove(resultScoreDocs.last());
                // add the new one ...
                resultScoreDocs.add(new SimpleResult(tmpScore, d, count));
                // and set our new distance border ...
                maxDistance = resultScoreDocs.last().getDistance();
            }
        }
//        System.out.println("** Creating response.");
        // todo: create response:
        for (Iterator<SimpleResult> it = resultScoreDocs.iterator(); it.hasNext(); ) {
            SimpleResult result = it.next();
            HashMap m = new HashMap(2);
            m.put("d", result.getDistance());
            m.put("id", result.getDocument().get("title"));
            rsp.add("r", m);
        }
        // rsp.add("Test-name", "Test-val");
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

package net.semanticmetadata.lire.solr;

import net.semanticmetadata.lire.imageanalysis.*;
import net.semanticmetadata.lire.impl.SimpleResult;
import org.apache.commons.codec.binary.Base64;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.SolrIndexSearcher;

import java.io.IOException;
import java.util.*;

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

    /**
     * Handles three types of requests.
     * <ol>
     *     <li>search by already extracted images.</li>
     *     <li>search by an image URL.</li>
     *     <li>Random results.</li>
     * </ol>
     * @param req
     * @param rsp
     * @throws Exception
     */
    @Override
    public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception {
        // (1) check if the necessary parameters are here
        if (req.getParams().get("hashes") != null) { // we are searching for hashes ...
            handleHashSearch(req, rsp);
        } else if (req.getParams().get("url") != null) { // we are searching for an image based on an URL
            handleUrlSearch(req, rsp);
        } else { // lets return random results.
            handleRandomSearch(req, rsp);
        }
    }

    private void handleRandomSearch(SolrQueryRequest req, SolrQueryResponse rsp) throws IOException {
        SolrIndexSearcher searcher = req.getSearcher();
        DirectoryReader indexReader = searcher.getIndexReader();
        double maxDoc = indexReader.maxDoc();
        int rows = 100;
        if (req.getParams().getInt("rows")!=null)
            rows = req.getParams().getInt("rows");
        LinkedList list = new LinkedList();
        while (list.size() < rows) {
            HashMap m = new HashMap(2);
            Document d = indexReader.document((int) Math.floor(Math.random()*maxDoc));
            m.put("id", d.getValues("id"));
            m.put("title", d.getValues("title"));
            list.add(m);
        }
        rsp.add("docs", list);
    }

    private void handleUrlSearch(SolrQueryRequest req, SolrQueryResponse rsp) {
        //To change body of created methods use File | Settings | File Templates.
    }

    private void handleHashSearch(SolrQueryRequest req, SolrQueryResponse rsp) throws IOException, IllegalAccessException, InstantiationException {
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
        LinkedList list = new LinkedList();
        for (Iterator<SimpleResult> it = resultScoreDocs.iterator(); it.hasNext(); ) {
            SimpleResult result = it.next();
            HashMap m = new HashMap(2);
            m.put("d", result.getDistance());
            m.put("id", result.getDocument().get("title"));
            list.add(m);
        }
        rsp.add("docs", list);
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

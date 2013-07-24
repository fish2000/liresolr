package net.semanticmetadata.lire.solr;

import net.semanticmetadata.lire.imageanalysis.*;
import net.semanticmetadata.lire.impl.SimpleResult;
import net.semanticmetadata.lire.indexing.hashing.BitSampling;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.TreeSet;

/**
 * This file is part of LIRE, a Java library for content based image retrieval.
 *
 * @author Mathias Lux, mathias@juggle.at, 07.07.13
 */

public class LireRequestHandler extends RequestHandlerBase {
    private int countRequests = 0;
    private static HashMap<String, Class> fieldToClass = new HashMap<String, Class>(5);
    private int defaultNumberOfResults = 60;

    static {
        fieldToClass.put("cl_ha", ColorLayout.class);
        fieldToClass.put("ph_ha", PHOG.class);
        fieldToClass.put("oh_ha", OpponentHistogram.class);
        fieldToClass.put("eh_ha", EdgeHistogram.class);
        fieldToClass.put("jc_ha", JCD.class);

        // one time hash function read ...
        try {
            BitSampling.readHashFunctions();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void init(NamedList args) {
        super.init(args);
    }

    /**
     * Handles three types of requests.
     * <ol>
     * <li>search by already extracted images.</li>
     * <li>search by an image URL.</li>
     * <li>Random results.</li>
     * </ol>
     *
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
        } else if (req.getParams().get("id") != null) { // we are searching for an image based on an URL
            handleIdSearch(req, rsp);
        } else { // lets return random results.
            handleRandomSearch(req, rsp);
        }
    }

    /**
     * Handles the get parameters id, field and rows.
     *
     * @param req
     * @param rsp
     * @throws IOException
     * @throws InstantiationException
     * @throws IllegalAccessException
     */
    private void handleIdSearch(SolrQueryRequest req, SolrQueryResponse rsp) throws IOException, InstantiationException, IllegalAccessException {
        SolrIndexSearcher searcher = req.getSearcher();
        TopDocs hits = searcher.search(new TermQuery(new Term("id", req.getParams().get("id"))), 1);
        String paramField = "cl_ha";
        if (req.getParams().get("field") != null)
            paramField = req.getParams().get("field");
        LireFeature queryFeature = (LireFeature) fieldToClass.get(paramField).newInstance();
        rsp.add("QueryField", paramField);
        rsp.add("QueryFeature", queryFeature.getClass().getName());

        if (hits.scoreDocs.length > 0) {
            Document d = searcher.getIndexReader().document(hits.scoreDocs[0].doc);
            String[] hashes = d.getValues(paramField)[0].trim().split(" ");
            String histogramFieldName = paramField.replace("_ha", "_hi");
            queryFeature.setByteArrayRepresentation(d.getBinaryValue(histogramFieldName).bytes,
                    d.getBinaryValue(histogramFieldName).offset, d.getBinaryValue(histogramFieldName).length);
            int paramRows = defaultNumberOfResults;
            if (req.getParams().getInt("rows") != null)
                paramRows = req.getParams().getInt("rows");
            BooleanQuery query = new BooleanQuery();
            for (int i = 0; i < hashes.length; i++) {
                // be aware that the hashFunctionsFileName of the field must match the one you put the hashes in before.
                hashes[i] = hashes[i].trim();
                if (hashes[i].length() > 0) {
                    query.add(new BooleanClause(new TermQuery(new Term(paramField, hashes[i].trim())), BooleanClause.Occur.SHOULD));
//                System.out.println("** " + paramField + ": " + hashes[i].trim());
                }
            }
            doSearch(rsp, searcher, paramField, paramRows, query, queryFeature);
        }
    }

    private void handleRandomSearch(SolrQueryRequest req, SolrQueryResponse rsp) throws IOException {
        SolrIndexSearcher searcher = req.getSearcher();
        DirectoryReader indexReader = searcher.getIndexReader();
        double maxDoc = indexReader.maxDoc();
        int paramRows = defaultNumberOfResults;
        if (req.getParams().getInt("rows") != null)
            paramRows = req.getParams().getInt("rows");
        LinkedList list = new LinkedList();
        while (list.size() < paramRows) {
            HashMap m = new HashMap(2);
            Document d = indexReader.document((int) Math.floor(Math.random() * maxDoc));
            m.put("id", d.getValues("id"));
            m.put("title", d.getValues("title"));
            list.add(m);
        }
        rsp.add("docs", list);
    }

    private void handleUrlSearch(SolrQueryRequest req, SolrQueryResponse rsp) throws IOException, InstantiationException, IllegalAccessException {
        SolrParams params = req.getParams();
        String paramUrl = params.get("url");
        String paramField = "cl_ha";
        if (req.getParams().get("field") != null)
            paramField = req.getParams().get("field");
        int paramRows = defaultNumberOfResults;
        if (params.get("rows") != null)
            paramRows = params.getInt("rows");
        BufferedImage img = ImageIO.read(new URL(paramUrl).openStream());
        LireFeature feat;
        // getting the right feature per field:
        if (paramField == null) feat = new EdgeHistogram();
        else {
            if (paramField.equals("cl_ha")) feat = new ColorLayout();
            else if (paramField.equals("jc_ha")) feat = new JCD();
            else if (paramField.equals("ph_ha")) feat = new PHOG();
            else if (paramField.equals("oh_ha")) feat = new OpponentHistogram();
            else feat = new EdgeHistogram();
        }
        feat.extract(img);
        int[] hashes = BitSampling.generateHashes(feat.getDoubleHistogram());
        BooleanQuery query = new BooleanQuery();
        for (int i = 0; i < hashes.length; i++) {
            // be aware that the hashFunctionsFileName of the field must match the one you put the hashes in before.
            query.add(new BooleanClause(new TermQuery(new Term(paramField, hashes[i] + "")), BooleanClause.Occur.SHOULD));
        }
        doSearch(rsp, req.getSearcher(), paramField, paramRows, query, feat);
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
        String paramField = "cl_ha";
        if (req.getParams().get("field") != null)
            paramField = req.getParams().get("field");
        int paramRows = defaultNumberOfResults;
        if (params.getInt("rows") != null)
            paramRows = params.getInt("rows");
        // create boolean query:
//        System.out.println("** Creating query.");
        BooleanQuery query = new BooleanQuery();
        for (int i = 0; i < hashes.length; i++) {
            // be aware that the hashFunctionsFileName of the field must match the one you put the hashes in before.
            hashes[i] = hashes[i].trim();
            if (hashes[i].length() > 0) {
                query.add(new BooleanClause(new TermQuery(new Term(paramField, hashes[i].trim())), BooleanClause.Occur.SHOULD));
//                System.out.println("** " + field + ": " + hashes[i].trim());
            }
        }
//        System.out.println("** Doing search.");

        // query feature
        LireFeature queryFeature = (LireFeature) fieldToClass.get(paramField).newInstance();
        queryFeature.setByteArrayRepresentation(featureVector);

        // get results:
        doSearch(rsp, searcher, paramField, paramRows, query, queryFeature);
    }

    private void doSearch(SolrQueryResponse rsp, SolrIndexSearcher searcher, String field, int maximumHits, BooleanQuery query, LireFeature queryFeature) throws IOException, IllegalAccessException, InstantiationException {
        // temp feature instance
        LireFeature tmpFeature = queryFeature.getClass().newInstance();
        TopDocs docs = searcher.search(query, 500);
        rsp.add("RawDocsCount", docs.scoreDocs.length+"");
//        System.out.println("** Query feature: " + queryFeature.getClass().getName() + ": " + Arrays.toString(queryFeature.getDoubleHistogram()));
//        System.out.println("** Doing re-rank.");
        // re-rank
        TreeSet<SimpleResult> resultScoreDocs = new TreeSet<SimpleResult>();
        float maxDistance = -1f;
        float tmpScore;

        String name = field.replace("_ha", "_hi");
        Document d;
//        System.out.println("** Iterating docs.");
        for (int i = 0; i < docs.scoreDocs.length; i++) {
//            System.out.println("** " + count);
//            System.out.println("** Getting document " + docs.scoreDocs[i].doc + " with score " + docs.scoreDocs[i].score);
            d = searcher.doc(docs.scoreDocs[i].doc);
//            System.out.println("** Getting data from field " + name);
//            tmpFeature.setByteArrayRepresentation(Base64.decodeBase64(d.getValues(name)[0]));
//            System.out.println("** id: " + d.getValues("id")[0]);
//            System.out.println("** Base64: " + org.apache.solr.common.util.Base64.byteArrayToBase64(d.getBinaryValue(name).bytes, d.getBinaryValue(name).offset, d.getBinaryValue(name).length));
//            System.out.println("** Feature: " + tmpFeature.getClass().getName());
            tmpFeature.setByteArrayRepresentation(d.getBinaryValue(name).bytes, d.getBinaryValue(name).offset, d.getBinaryValue(name).length);
            tmpScore = queryFeature.getDistance(tmpFeature);
//            System.out.println("** Score: " + tmpScore + " - max Score: " + maxDistance);
            if (resultScoreDocs.size() < maximumHits) {
                resultScoreDocs.add(new SimpleResult(tmpScore, d, docs.scoreDocs[i].doc));
                maxDistance = resultScoreDocs.last().getDistance();
            } else if (tmpScore < maxDistance) {
//                if it is nearer to the sample than at least one of the current set:
//                remove the last one ...
                resultScoreDocs.remove(resultScoreDocs.last());
//                add the new one ...
                resultScoreDocs.add(new SimpleResult(tmpScore, d, docs.scoreDocs[i].doc));
//                and set our new distance border ...
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
            m.put("id", result.getDocument().get("id"));
            m.put("title", result.getDocument().get("title"));
//            m.put(field, result.getDocument().get(field));
//            m.put(field.replace("_ha", "_hi"), result.getDocument().getBinaryValue(field));
            list.add(m);
        }
        rsp.add("docs", list);
        // rsp.add("Test-name", "Test-val");
    }

    @Override
    public String getDescription() {
        return "LIRE Request Handler to add images to an index and search them. Search images by id, by url and by extracted features.";
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

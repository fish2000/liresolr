package net.semanticmetadata.lire.solr;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;

/**
 * This file is part of LIRE, a Java library for content based image retrieval.
 *
 * @author Mathias Lux, mathias@juggle.at, 07.07.13
 */

public class SearchImages {
    public static void main(String[] args) throws IOException, ParserConfigurationException, SAXException {
        // http://localhost:8080/solr/lire/query?q=hashes%3A1152++hashes%3A605++hashes%3A96++hashes%3A275++&wt=xml
        String hashes = "1152  605  96  275  2057  3579  3950  2831  2367  3169  3292  974  2465  1573  2933  3125  314  2158  3532  974  2198  2315  3013  3302  3316  1467  2213  818  3  1083  18  2604  327  1370  593  3677  464  79  256  984  2496  1124  855  2091  780  1941  1887  1145  1396  4016  2406  2227  1532  2598  215  1375  171  2516  1698  368  2350  3799  223  1471  2083  1051  3015  3789  3374  1442  3991  3575  1452  751  428  3103  1182  2241  474  275  3678  3970  559  3394  2662  2361  2048  1083  181  1483  3903  3331  2363  756  558  2838  3984  1878  2667  3333  1473  2136  3499  3873  1437  3091  1287  948  46  3660  3003  1572  1185  2231  2622  257  3538  3632  3989  1180  3928  3144  1492  3941  3253  3498  2721  1036  22  1020  725  1431  3821  2248  2542  3659  2849  524  2967  1  2493  3620  2951  3584  1641  3873  2087  1506  1489  3064";
        String[] split = hashes.split(" ");
        String query = "";
        for (int i = 0; i < split.length; i++) {
            String s = split[i];
            if (s.trim().length() > 0) query += " hashes:" + s.trim();
        }
        URL u = new URL("http://localhost:8080/solr/lire/query?q=" + URLEncoder.encode(query, "utf-8") + "&wt=xml&rows=500");
        InputStream in = u.openStream();
        SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
        saxParser.parse(in, new SolrResponseHandler());
        System.out.println("******");
    }


}

class SolrResponseHandler extends DefaultHandler {
    boolean isInDocument;
    boolean isInHistogram;
    boolean isInId;
    int countResults = 0;
    StringBuilder hist = new StringBuilder(256);
    StringBuilder id = new StringBuilder(256);

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        if (qName.startsWith("doc")) {
            isInDocument = true;
        } else if (isInDocument && qName.startsWith("str")) {
            if (attributes.getValue("name") != null && attributes.getValue("name").equals("histogram")) {
                isInHistogram = true;
                hist.delete(0, hist.length());
            } else if (attributes.getValue("name") != null && attributes.getValue("name").equals("id")) {
                isInId = true;
                id.delete(0, id.length());
            }

        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (qName.startsWith("doc")) {
            isInDocument = false;
            countResults++;
            System.out.println(id.toString() + ", " + hist.toString());
        } else if (qName.startsWith("str")) {
            isInHistogram = false;
            isInId = false;
        } else if (qName.startsWith("result")) {
            System.out.println(countResults + " results found");
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (isInHistogram) hist.append(ch, start, length);
        if (isInId) id.append(ch, start, length);
    }
}

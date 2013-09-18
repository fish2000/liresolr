package net.semanticmetadata.lire.solr;

import org.apache.commons.codec.binary.Base64;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.search.FunctionQParser;
import org.apache.solr.search.SyntaxError;
import org.apache.solr.search.ValueSourceParser;

/**
 * <p>A query function for sorting results based on the LIRE CBIR functions.
 * Implementation based partially on the outdated guide given on http://www.supermind.org/blog/756,
 * comments on the mailing list provided from Chris Hostetter, and the 4.4 Solr & Lucene source. </p>
 *
 * <p>Do not forget to add the function parser to the lsolrconfig.xml file like this:<br/>
 * <valueSourceParser name="lirefunc" class="net.semanticmetadata.lire.solr.LireValueSourceParser" /></p>
 *
 * <p>To use the function getting a distance to a reference image use it like:<br/>
 * http://localhost:9000/solr/lire/select?q=*:*&fl=id,lirefunc(cl,"FQY5DhMYDg0ODg0PEBEPDg4ODg8QEgsgEBAQEBAgEBAQEBA%3D")<br/>
 * The first parameter gives the field (cl, ph, eh, or jc), the second gives the byte[] representation of the
 * histogram in Base64 encoding</p>
 * @author Mathias Lux, 17.09.13, 12:21
 */
public class LireValueSourceParser extends ValueSourceParser {

    public void init(NamedList namedList) {

    }

    @Override
    public ValueSource parse(FunctionQParser fp) throws SyntaxError {
        String field=fp.parseArg();                          // eg. cl_hi
        byte[] hist= Base64.decodeBase64(fp.parseArg());     // eg. FQY5DhMYDg0ODg0PEBEPDg4ODg8QEgsgEBAQEBAgEBAQEBA=
        return new LireValueSource(field, hist);
    }
}

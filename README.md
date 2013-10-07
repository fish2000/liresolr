LIRE Solr Integration Project
=============================

Includes a RequestHandler and some utility classes for a fast start.

The request handler supports four different types of queries

1.  Get random images ...
2.  Get images that are looking like the one with id ...
3.  Get images looking like the one found at url ...
4.  Get images with a feature vector like ...

Preliminaries
-------------
Supported values for feature field parameters, e.g. lireq?field=cl_ha:

-  **cl_ha** .. ColorLayout
-  **ph_ha** .. PHOG
-  **oh_ha** .. OpponentHistogram
-  **eh_ha** .. EdgeHistogram
-  **jc_ha** .. JCD

Getting random images
---------------------
Returns randomly chosen images from the index.

Parameters:

-   **rows** ... indicates how many results should be returned (optional, default=60). Example: lireq?rows=30

Search by ID
------------
Returns images that look like the one with the given ID.

Parameters:

-   **id** .. the ID of the image used as a query as stored in the "id" field in the index.
-   **field** .. gives the feature field to search for (optional, default=cl_ha, values see above)
-   **rows** .. indicates how many results should be returned (optional, default=60).

Search by URL
-------------
Returns images that look like the one found at the given URL.

Parameters:

-   **url** .. the URL of the image used as a query. Note that the image has to be accessible by the web server Java has to be able to read it.
-   **field** .. gives the feature field to search for (optional, default=cl_ha, values see above)
-   **rows** .. indicates how many results should be returned (optional, default=60).

Search by feature vector
------------------------
Returns an image that looks like the one the given features were extracted. This method is used if the client
extracts the features from the image, which makes sense if the image should not be submitted.

Parameters:

-  **hashes** .. Hashes of the image feature as returned by BitSampling#generateHashes(double[]) as a String of white space separated numbers.
-  **feature** .. Base64 encoded feature histogram from LireFeature#getByteArrayRepresentation().
-  **field** .. gives the feature field to search for (optional, default=cl_ha, values see above)
-  **rows** .. indicates how many results should be returned (optional, default=60).


Installation
============

First run the dist task to create a single jar. This should be integrated in the Solr class-path. Then add
the new request handler has to be registered in the solrconfig.xml file:

     <requestHandler name="/lireq" class="net.semanticmetadata.lire.solr.LireRequestHandler">
        <lst name="defaults">
          <str name="echoParams">explicit</str>
          <str name="wt">json</str>
          <str name="indent">true</str>
        </lst>
     </requestHandler>

Use of the request handler is detailed above.

You'll also need the respective fields in the schema.xml file:

    <fields>
       <!-- file path for ID -->
       <field name="id" type="string" indexed="true" stored="true" required="true" multiValued="false" />
       <!-- the sole file name -->
       <field name="title" type="text_general" indexed="true" stored="true" multiValued="true"/>
       <!-- Edge Histogram -->
       <field name="eh_ha" type="text_ws" indexed="true" stored="false" required="false"/>
       <field name="eh_hi" type="binaryDV"  indexed="false" stored="true" required="false"/>
       <!-- ColorLayout -->
       <field name="cl_ha" type="text_ws" indexed="true" stored="false" required="false"/>
       <field name="cl_hi" type="binaryDV"  indexed="false" stored="true" required="false"/>
       <!-- PHOG -->
       <field name="ph_ha" type="text_ws" indexed="true" stored="false" required="false"/>
       <field name="ph_hi" type="binaryDV"  indexed="false" stored="true" required="false"/>
       <!-- JCD -->
       <field name="jc_ha" type="text_ws" indexed="true" stored="false" required="false"/>
       <field name="jc_hi" type="binaryDV"  indexed="false" stored="true" required="false"/>
       <!-- OpponentHistogram -->
       <!--field name="oh_ha" type="text_ws" indexed="true" stored="false" required="false"/-->
       <!--field name="oh_hi" type="binary"  indexed="false" stored="true" required="false"/-->
       <!-- Needed for SOLR -->
       <field name="_version_" type="long" indexed="true" stored="true"/>
    </fields>

There is also a sort function based on LIRE. The function parser needs to be added to the
solarconfig.xml file like this:

      <valueSourceParser name="lirefunc"
        class="net.semanticmetadata.lire.solr.LireValueSourceParser" />

Then the function lirefunc(arg1,arg2) is available for function queries. Two arguments are necessary and are defined as:

-  Feature to be used for computing the distance between result and reference image. Possible values are {cl, ph, eh, jc}
-  Actual Base64 encoded feature vector of the reference image. It can be obtained by calling LireFeature.getByteRepresentation() and by Base64 encoding the resulting byte[] data.

Examples:

-  [solrurl]/select?q=*:*&fl=id,lirefunc(cl,"FQY5DhMYDg...AQEBA%3D") – adding the distance to the reference image to the results
-  [solrurl]/select?q=*:*&sort=lirefunc(cl,"FQY5DhMYDg...AQEBA%3D")+asc – sorting the results based on the distance to the reference image



Indexing
========

Check ParallelSolrIndexer.java for indexing. It creates XML documents (either one per image or one single large file)
to be sent to the Solr Server.

*Mathias Lux, 2013-10-07*
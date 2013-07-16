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


*Mathias Lux, 2013-07-12*
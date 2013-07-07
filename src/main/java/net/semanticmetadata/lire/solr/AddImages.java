package net.semanticmetadata.lire.solr;

import net.semanticmetadata.lire.imageanalysis.ColorLayout;
import net.semanticmetadata.lire.imageanalysis.EdgeHistogram;
import net.semanticmetadata.lire.indexing.hashing.BitSampling;
import net.semanticmetadata.lire.utils.FileUtils;
import net.semanticmetadata.lire.utils.SerializationUtils;
import org.apache.commons.codec.binary.Base64;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * This file is part of LIRE, a Java library for content based image retrieval.
 *
 * @author Mathias Lux, mathias@juggle.at, 22.06.13
 */

// ADDING DOCUMENTS:
//<add>
//<doc>
//<field name="employeeId">05991</field>
//<field name="office">Bridgewater</field>
//<field name="skills">Perl</field>
//<field name="skills">Java</field>
//</doc>
//        [<doc> ... </doc>[<doc> ... </doc>]]
//</add>

// DELETING DOCUMENTS:
//<delete>
//        <id>05991</id><id>06000</id>
//        <query>office:Bridgewater</query>
//        <query>office:Osaka</query>
//</delete>

// <delete><query>id:*</query></delete>

public class AddImages {
    public static void main(String[] args) throws IOException {
        BitSampling.readHashFunctions();
        ArrayList<File> files = FileUtils.getAllImageFiles(new File("D:\\DataSets\\WIPO\\CA\\converted-4"), true);
//        ArrayList<File> files = FileUtils.getAllImageFiles(new File("..\\Lire\\testdata\\wang-1000"), true);
        for (Iterator<File> iterator = files.iterator(); iterator.hasNext(); ) {
            File file = iterator.next();
            URL u = new URL("http://localhost:8080/solr/lire/update?stream.body=" +
                    URLEncoder.encode(createAddDoc(file).toString(), "utf-8"));
            InputStream in = u.openStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = reader.readLine())!=null) {
                System.out.println(line);
            }
            System.out.println("******");
        }
        System.out.println("http://localhost:8080/solr/lire/update?stream.body=" + URLEncoder.encode("<commit/>", "utf-8"));
//        System.out.println("http://localhost:8080/solr/lire/update?stream.body=" + URLEncoder.encode("<commit/>", "utf-8"));
    }

    static StringBuilder createAddDoc(File image) throws IOException {
        BufferedImage img = ImageIO.read(image);
        StringBuilder result = new StringBuilder(200);
        result.append("<add>\n");
        result.append("\t<doc>\n");
        // id and file name ...
        result.append("\t\t<field name=\"id\">");
        result.append(image.getCanonicalPath());
        result.append("</field>\n");
        result.append("\t\t<field name=\"title\">");
        result.append(image.getName());
        result.append("</field>\n");
        // feature:
        ColorLayout eh = new ColorLayout ();
        eh.extract(img);
        result.append("\t\t<field name=\"histogram\">");
        result.append(Base64.encodeBase64String(eh.getByteArrayRepresentation()));
        result.append("</field>\n");
        // hashes
        result.append("\t\t<field name=\"hashes\">");
        result.append(SerializationUtils.arrayToString(BitSampling.generateHashes(eh.getDoubleHistogram())));
        result.append("</field>\n");
        result.append("\t</doc>\n");
        result.append("</add>");


        return result;
    }
}

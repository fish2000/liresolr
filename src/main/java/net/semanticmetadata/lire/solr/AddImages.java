package net.semanticmetadata.lire.solr;

import net.semanticmetadata.lire.imageanalysis.*;
import net.semanticmetadata.lire.indexing.hashing.BitSampling;
import net.semanticmetadata.lire.utils.FileUtils;
import net.semanticmetadata.lire.utils.SerializationUtils;
import org.apache.commons.codec.binary.Base64;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

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
    static String baseURL = "http://localhost:9000/solr/lire";

    public static void main(String[] args) throws IOException, InterruptedException {
        BitSampling.readHashFunctions();
        LinkedList<Thread> threads = new LinkedList<Thread>();
        for (int j = 0; j<18; j++) {
            final int tz = j;
            Thread t = new Thread(){
                @Override
                public void run() {
                    try {
                        List<File> files = FileUtils.getAllImageFiles(new File("E:\\WIPO-conv\\converted-"+tz), true);
                        int count = 0;
                        BufferedWriter br = new BufferedWriter(new FileWriter("add-"+tz+".xml", false));
                        br.write("<add>\n");
                        for (Iterator<File> iterator = files.iterator(); iterator.hasNext(); ) {
                            File file = iterator.next();
                            br.write(createAddDoc(file).toString());
                            count++;
//                            if (count % 1000 == 0) System.out.print('.');
                        }
                        br.write("</add>\n");
                        br.close();
                    } catch (IOException e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                }
            };
            t.start();
            threads.add(t);
        }
        for (Iterator<Thread> iterator = threads.iterator(); iterator.hasNext(); ) {
            Thread next = iterator.next();
            next.join();
        }
    }

    private static StringBuilder createAddDoc(File image) throws IOException {
        BufferedImage img = ImageIO.read(image);
        StringBuilder result = new StringBuilder(200);
//        result.append("<add>\n");
        result.append("\t<doc>\n");
        // id and file name ...
        result.append("\t\t<field name=\"id\">");
        result.append(image.getCanonicalPath());
        result.append("</field>\n");
        result.append("\t\t<field name=\"title\">");
        result.append(image.getName());
        result.append("</field>\n");
        // features:
        getFields(img, result, new ColorLayout(), "cl_hi", "cl_ha");
        getFields(img, result, new EdgeHistogram(), "eh_hi", "eh_ha");
        getFields(img, result, new JCD(), "jc_hi", "jc_ha");
        getFields(img, result, new PHOG(), "ph_hi", "ph_ha");
        getFields(img, result, new OpponentHistogram(), "oh_hi", "oh_ha");
        // close doc ...
        result.append("\t</doc>\n");
//        result.append("</add>");
        return result;
    }

    private static void getFields(BufferedImage img, StringBuilder result, LireFeature feature, String histogramField, String hashesField) {
        feature.extract(img);
        result.append("\t\t<field name=\"" + histogramField + "\">");
        result.append(Base64.encodeBase64String(feature.getByteArrayRepresentation()));
        result.append("</field>\n");
        result.append("\t\t<field name=\"" + hashesField + "\">");
        result.append(SerializationUtils.arrayToString(BitSampling.generateHashes(feature.getDoubleHistogram())));
        result.append("</field>\n");
    }

}

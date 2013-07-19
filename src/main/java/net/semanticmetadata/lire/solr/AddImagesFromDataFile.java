package net.semanticmetadata.lire.solr;

import net.semanticmetadata.lire.imageanalysis.*;
import net.semanticmetadata.lire.indexing.hashing.BitSampling;
import net.semanticmetadata.lire.indexing.tools.Extractor;
import net.semanticmetadata.lire.utils.SerializationUtils;
import org.apache.commons.codec.binary.Base64;

import javax.imageio.ImageIO;
import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.zip.GZIPInputStream;

/**
 * Created with IntelliJ IDEA.
 * User: mlux
 * Date: 08.07.13
 * Time: 11:26
 * To change this template use File | Settings | File Templates.
 */
public class AddImagesFromDataFile {
    boolean verbose = true;
    private static HashMap<Class, String> classToPrefix = new HashMap<Class, String>(5);

    static {
        classToPrefix.put(ColorLayout.class, "cl");
        classToPrefix.put(EdgeHistogram.class, "eh");
        classToPrefix.put(PHOG.class, "ph");
        classToPrefix.put(OpponentHistogram.class, "oh");
        classToPrefix.put(JCD.class, "jc");
    }

    public static void main(String[] args) throws IOException, IllegalAccessException, ClassNotFoundException, InstantiationException {
//        BitSampling.setNumFunctionBundles(80);
//        BitSampling.generateHashFunctions("BitSampling.obj");
        BitSampling.readHashFunctions();
        AddImagesFromDataFile a = new AddImagesFromDataFile();
        a.createXml(new File("D:/Temp"), new File("D:\\DataSets/wipo_v7.out"));
    }


    public void createXml(File outDirectory, File inputFile) throws IOException, InstantiationException, IllegalAccessException, ClassNotFoundException {
        InputStream in = new FileInputStream(inputFile);
        byte[] tempInt = new byte[4];
        int tmp, tmpFeature;
        int count = 0;
        byte[] temp = new byte[10 * 1024 * 1024];
        // read file hashFunctionsFileName length:
        FileWriter out = new FileWriter(outDirectory.getPath() + "/data_001.xml", false);
        int fileCount = 1;
        out.write("<add>\n");
        while ((tmp = in.read(tempInt, 0, 4)) > 0) {
            tmp = SerializationUtils.toInt(tempInt);
            // read file name:
            in.read(temp, 0, tmp);
            String filename = new String(temp, 0, tmp);
            // normalize Filename to full path.
            File file = new File(filename);
            out.write("\t<doc>\n");
            // id and file name ...
            out.write("\t\t<field name=\"id\">");
            out.write(file.getCanonicalPath().replace("D:\\DataSets\\WIPO-", "").replace('\\', '/'));
            out.write("</field>\n");
            out.write("\t\t<field name=\"title\">");
            out.write(file.getName());
            out.write("</field>\n");
//            System.out.print(filename);
            while ((tmpFeature = in.read()) < 255) {
//                System.out.print(", " + tmpFeature);
                LireFeature f = (LireFeature) Class.forName(Extractor.features[tmpFeature]).newInstance();
                // byte[] length ...
                in.read(tempInt, 0, 4);
                tmp = SerializationUtils.toInt(tempInt);
                // read feature byte[]
                int read = in.read(temp, 0, tmp);
                if (read != tmp) System.err.println("!!!");
                f.setByteArrayRepresentation(temp, 0, tmp);
                addToDocument(f, out, file);
//                d.add(new StoredField(Extractor.featureFieldNames[tmpFeature], f.getByteArrayRepresentation()));
            }
            out.write("\t</doc>\n");
            count++;
            if (count % 100000 == 0) {
//                break;
                out.write("</add>\n");
                out.close();
                fileCount++;
                out = new FileWriter(outDirectory.getPath() + "/data_0" + ((fileCount < 10) ? "0" + fileCount : fileCount) + ".xml", false);
                out.write("<add>\n");
            }
            if (verbose) {
                if (count % 1000 == 0) System.out.print('.');
                if (count % 10000 == 0) System.out.println(" " + count);
            }
        }
        if (verbose) System.out.println(" " + count);
        out.write("</add>\n");
        out.close();
        in.close();
    }

    private void addToDocument(LireFeature feature, Writer out, File file) throws IOException {
        try {
            LireFeature f1 = feature.getClass().newInstance();
            f1.extract(ImageIO.read(file));
            float distance = f1.getDistance(feature);
            if (distance != 0) {
                System.out.println("D: " + Arrays.toString(feature.getDoubleHistogram()) + "\n" +
                        "E: " + Arrays.toString(f1.getDoubleHistogram()) + "\n" +
                        "Problem with " + f1.getClass().getName() + " at file " + file.getPath() + ", distance=" + distance
                );
            }
        } catch (Exception e) {
            e.printStackTrace();

        }

        String histogramField = classToPrefix.get(feature.getClass()) + "_hi";
        String hashesField = classToPrefix.get(feature.getClass()) + "_ha";

        out.write("\t\t<field name=\"" + histogramField + "\">");
        out.write(Base64.encodeBase64String(feature.getByteArrayRepresentation()));
        out.write("</field>\n");
        out.write("\t\t<field name=\"" + hashesField + "\">");
        out.write(SerializationUtils.arrayToString(BitSampling.generateHashes(feature.getDoubleHistogram())));
        out.write("</field>\n");

//        if (classToPrefix.get(feature.getClass()).equals("eh")) System.out.println(classToPrefix.get(feature.getClass()) + " " + Base64.encodeBase64String(feature.getByteArrayRepresentation()));

    }
}

/*
 * Copyright (C) 2014 balnave
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package affirmation.runners;

import affirmation.results.AffirmationResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import semblance.io.IReader;
import semblance.io.MultipartURLWriter;
import semblance.io.ReaderFactory;
import semblance.results.IResult;
import semblance.runners.Runner;

/**
 * Uses the W3C web-service to validate a source file
 *
 * @author balnave
 */
public class AffirmationRunner extends Runner {
    
    public static final String KEY_W3C_SERVICES = "services";
    public static final String KEY_W3C_HTML_SERVICES = "html";
    public static final String KEY_W3C_CSS_SERVICES = "css";
    public static final String KEY_URLS = "urls";
    public static final String KEY_MESSAGES_TO_IGNORE = "ignore";

    private final ReaderFactory rf = new ReaderFactory();

    public AffirmationRunner(Map<String, Object> config) {
        super(config);
    }

    public AffirmationRunner(String configUrlOrFilePath) {
        super(configUrlOrFilePath);
    }

    @Override
    public List<IResult> run() throws Exception {
        String w3cServiceUrl = (String) getConfigValue("w3cServiceUrl", "");
        List<String> urls = (List<String>) getConfigValue(KEY_URLS, new ArrayList<String>());
        List<String> ignoreMessages = (List<String>) getConfigValue(KEY_MESSAGES_TO_IGNORE, new ArrayList<String>());
        //
        // loop through each url
        for (final String url : urls) {
            IReader reader = rf.getReader(url);
            String html = reader.load();
            if (!html.isEmpty()) {
                MultipartURLWriter loader = new MultipartURLWriter(w3cServiceUrl, "UTF-8");
                loader.addFormField("output", "soap12");
                loader.addFormField("outline", "1");
                loader.addFormField("charset", "UTF-8");
                loader.addFormField("doctype", "inline");
                loader.addFilePart("uploaded_file", url.endsWith("/") ? url + "index.html" : url, html);
                String response = loader.sendAndReceive();
                Document document = parseSoapResponse(response);
                NodeList elementList = document.getElementsByTagName("m:validity");
                results.addAll(getWarningsOrError(document.getElementsByTagName("m:error"), url, true, ignoreMessages));
                results.addAll(getWarningsOrError(document.getElementsByTagName("m:warning"), url, true, ignoreMessages));
                if (elementList.getLength() == 1) {
                    boolean isValid = elementList.item(0).getTextContent().equalsIgnoreCase("true");
                    results.add(new AffirmationResult(url, isValid, "Validity is " + isValid));
                } else {
                    results.add(new AffirmationResult(url, false, "No m:validity node found"));
                }
            } else {
                results.add(new AffirmationResult(url, false, "File response is empty"));
            }
            // sleep for 1000ms as requested by W3C API
            Thread.sleep(1000);
        }
        return results;
    }

    private List<AffirmationResult> getWarningsOrError(NodeList list, String url, boolean fail, List<String> ignoreList) {
        List<AffirmationResult> tmpResults = new ArrayList<AffirmationResult>();
        for (int i = 0; i < list.getLength(); i++) {
            Node item = list.item(i);
            NodeList children = item.getChildNodes();
            String msg = "";
            String reason = "";
            int lineInt = 0;
            int paraInt = 0;
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                String childText = child.getTextContent();
                if (child.getNodeName().equalsIgnoreCase("m:line")) {
                    lineInt = Integer.valueOf(childText.isEmpty() ? "0" : childText);
                } else if (child.getNodeName().equalsIgnoreCase("m:col")) {
                    paraInt = Integer.valueOf(childText.isEmpty() ? "0" : childText);
                } else if (child.getNodeName().equalsIgnoreCase("m:message")) {
                    msg = childText;
                } else if (child.getNodeName().equalsIgnoreCase("m:explanation")) {
                    reason = childText;
                }
            }
            boolean ignoreThisResult = false;
            if (ignoreList != null) {
                for (String ignoreMsg : ignoreList) {
                    if (msg.toLowerCase().contains(ignoreMsg.toLowerCase()) 
                            || reason.toLowerCase().contains(ignoreMsg.toLowerCase())) {
                        ignoreThisResult = true;
                        break;
                    }
                }
            }
            if (!ignoreThisResult) {
                tmpResults.add(new AffirmationResult(url,
                        !fail,
                        reason,
                        msg,
                        lineInt,
                        paraInt));
            }
        }
        return tmpResults;
    }

    private Document parseSoapResponse(String response) {
        DocumentBuilderFactory docFactory;
        DocumentBuilder docBuilder;
        Document document;
        InputStream stream;
        try {
            docFactory = DocumentBuilderFactory.newInstance();
            docBuilder = docFactory.newDocumentBuilder();
            stream = new ByteArrayInputStream(response.getBytes("UTF-8"));
            document = docBuilder.parse(stream);
            return document;
        } catch (ParserConfigurationException ex) {
            System.err.println("ParserConfigurationException Error" + ex.getMessage());
        } catch (SAXException ex) {
            System.err.println("SAXException Error" + ex.getMessage());
        } catch (IOException ex) {
            System.err.println("IOException Error" + ex.getMessage());
        }
        return null;
    }

}

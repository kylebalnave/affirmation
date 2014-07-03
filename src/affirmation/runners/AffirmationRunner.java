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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import semblance.io.IReader;
import semblance.io.MultipartURLWriter;
import semblance.io.ReaderFactory;
import semblance.results.ErrorResult;
import semblance.results.FailResult;
import semblance.results.IResult;
import semblance.results.PassResult;
import semblance.runners.Runner;

/**
 * Uses the W3C web-service to validate a source file A single Thread so we
 * don't hit the service too hard I'm nice like that
 *
 * @author balnave
 */
public class AffirmationRunner extends Runner {

    public static final String KEY_W3C_SERVICE = "w3cServiceUrl";
    public static final String KEY_URLS = "urls";
    public static final String KEY_MESSAGES_TO_IGNORE = "ignore";

    private final String ATTR_CLASS = "class";

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        callRunnerSequence(AffirmationRunner.class, args);
    }

    /**
     * Constructor
     *
     * @param config
     */
    public AffirmationRunner(Map<String, Object> config) {
        super(config);
    }

    /**
     * Constructor
     *
     * @param configUrlOrFilePath
     */
    public AffirmationRunner(String configUrlOrFilePath) {
        super(configUrlOrFilePath);
    }

    @Override
    public List<IResult> call() throws Exception {
        String w3cServiceUrl = (String) getConfigValue(KEY_W3C_SERVICE, "");
        List<String> urls = (List<String>) getConfigValue(KEY_URLS, new ArrayList<String>());
        List<String> ignoreMessages = (List<String>) getConfigValue(KEY_MESSAGES_TO_IGNORE, new ArrayList<String>());
        if (!w3cServiceUrl.isEmpty()) {
            // loop through each url
            for (final String url : urls) {
                IReader reader = ReaderFactory.getReader(url);
                String html = reader.load();
                if (!html.isEmpty()) {
                    MultipartURLWriter loader = new MultipartURLWriter(w3cServiceUrl, "UTF-8");
                    loader.addFormField("outline", "1");
                    loader.addFormField("charset", "UTF-8");
                    loader.addFormField("doctype", "inline");
                    loader.addFilePart("uploaded_file", url.endsWith("/") ? url + "index.html" : url, html);
                    String response = loader.sendAndReceive();
                //
                    // create an instance of HtmlCleaner
                    HtmlCleaner cleaner = new HtmlCleaner();
                    TagNode dom = cleaner.clean(response);
                    TagNode[] nodes = dom.getElementsByAttValue("id", "results", true, true);
                    if (nodes.length == 1 && nodes[0].getAttributeByName(ATTR_CLASS).contains("valid")) {
                        results.add(new PassResult(url, String.format("Invalid markup '%s'", nodes[0].getText())));
                    } else if (nodes.length == 1) {
                        results.add(new ErrorResult(url, String.format("Invalid markup '%s'", nodes[0].getText())));
                    } else {
                        results.add(new ErrorResult(url, "Expecting a single #results node"));
                    }
                //
                    // Add Validation Errors and Warnings
                    addFailures(dom, url, ignoreMessages);
                    addWarnings(dom, url);
                } else {
                    results.add(new ErrorResult(url, "File response is empty"));
                }
                // sleep for 1000ms as requested by W3C API
                Thread.sleep(1000);
            }
        } else {
            results.add(new ErrorResult(getClass().getName(), String.format("Service URL %s is required", KEY_W3C_SERVICE)));
        }
        return results;
    }

    private void addFailures(TagNode dom, String url, List<String> ignorableMessages) {
        TagNode[] errors = dom.getElementsByAttValue(ATTR_CLASS, "msg_err", true, true);
        for (TagNode node : errors) {
            String em = "";
            String msg = "";
            String pre = "";
            for (TagNode child : node.getChildTags()) {
                String tName = child.getName();
                String tText = child.getText().toString();
                if (em.isEmpty()) {
                    em = tName.equalsIgnoreCase("em") ? tText : "";
                }
                if (msg.isEmpty()) {
                    msg = tName.equalsIgnoreCase("span") && child.getAttributeByName(ATTR_CLASS).contains("msg") ? tText : "";
                }
                if (pre.isEmpty()) {
                    pre = tName.equalsIgnoreCase("pre") ? tText : "";
                }
            }
            if (hasIgnorableMessage(msg, ignorableMessages)) {
                results.add(new PassResult(url, "Ignored Message: " + pre, "Ignored Message: " + msg));
            } else {
                results.add(new FailResult(url, pre, msg));
            }
        }
    }

    private void addWarnings(TagNode dom, String url) {
        TagNode[] warnings = dom.getElementsByAttValue(ATTR_CLASS, "msg_warn", true, true);
        for (TagNode node : warnings) {
            String em = "";
            String msg = "";
            for (TagNode child : node.getChildTags()) {
                String tName = child.getName();
                String tText = child.getText().toString();
                if (em.isEmpty()) {
                    em = tName.equalsIgnoreCase("em") ? tText : "";
                }
                if (msg.isEmpty()) {
                    msg = tName.equalsIgnoreCase("span") && child.getAttributeByName(ATTR_CLASS).contains("msg") ? tText : "";
                }
            }
            results.add(new PassResult(url, msg, msg));
        }
    }

    /**
     * Can a message be ignored from errors
     *
     * @param msg
     * @param ignoredMessages
     * @return
     */
    private boolean hasIgnorableMessage(String msg, List<String> ignoredMessages) {
        boolean result = false;
        for (String ignorable : ignoredMessages) {
            if (msg.toLowerCase().contains(ignorable.toLowerCase())) {
                result = true;
            }
        }
        return result;
    }
}

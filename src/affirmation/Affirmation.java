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
package affirmation;

import affirmation.runners.AffirmationRunner;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import semblance.io.URLReader;
import semblance.reporters.Report;
import semblance.reporters.SystemLogReport;
import semblance.results.IResult;

/**
 * Uses the W3C web-service to validate a source file
 *
 * @author balnave
 */
public class Affirmation {

    /**
     * @param args the command line arguments
     * @throws java.io.FileNotFoundException
     */
    public static void main(String[] args) throws FileNotFoundException {
        String configUrlOrFilePath = "./config.json";
        String action = "dist";
        int argIndex = 0;
        for (String arg : args) {
            if (args.length >= argIndex + 1) {
                if (arg.equalsIgnoreCase("-cf") || arg.equalsIgnoreCase("-config")) {
                    configUrlOrFilePath = args[argIndex + 1];
                } else if (arg.equalsIgnoreCase("-proxy")) {
                    URLReader.setProxyDetails(args[argIndex + 1], Integer.valueOf(args[argIndex + 2]));
                }
            }
            argIndex++;
        }
        AffirmationRunner runner = new AffirmationRunner(configUrlOrFilePath);
        try {
            List<IResult> results = runner.run();
            runner.report();
            //
            // log the summary of all results
            Report report = new SystemLogReport(results);
            report.out();
        } catch (Exception ex) {
            Logger.getLogger(AffirmationRunner.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
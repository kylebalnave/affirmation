Affirmation
=========

[1]: https://github.com/kylebalnave/semblance        "Semblance"

A command line tool and [Semblance][1] Runner that validates HTML using the W3C validator service.  

### Commandline Usage

    java -jar dist/Affirmation.jar -config ./config.json

### Example Config

The below configuration will validate [BBC Homepage](http://www.bbc.co.uk/) and output a Junit Report

    {
        "w3cServiceUrl": "http://validator.w3.org/check",
        "urls": [
            "http://www.bbc.co.uk/"
        ],
        "ignore": [
            "Syntax of list of link-type keywords",
            "Mistakes that can cause this error include"
        ],
        "reports": [
            {
                "className": "semblance.reporters.JunitReport",
                "out": "./reports/w3c.junit"
            }
        ]
    }	

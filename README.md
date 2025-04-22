## The idea

When we work with SAP CI (aka SAP CPI) we often need to use groovy scripting to manipulate data.
In this days we have tool like https://groovyide.com/cpi to help us with that. But I always missed the ability to use vscode and have strong recommendations or even use github copilot or any other agent.

To fix this I create a very small class that allows to use similar code to the one used in normal groovy scripts.
From the SAP specific classes I would say that 80% of the ones I use the mosts are the ones related to get and set properties, headers and body, and this is what this class does.

## How to use it

1. You need to have a groovy environment setup. You can use the one from SAP CPI or you can use the one from https://groovy-lang.org/download.html. I recommend to use the one from SAP CPI as it is the same as the one used in the cloud.
2. You need to clone this repository to your local machine.
3. After you have this repository open it in your terminal can you need to compile the class by running the following command:

```bash
groovyc message.groovy your_script.groovy
```

4. The script main.groovy is only a example of how to use the class. You can use it as a template to create your own scripts.

If you have any questions or suggestions please feel free to open an issue in the repository. I will be happy to help you.

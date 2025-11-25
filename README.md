# flow-forge-engine: Execution Module for FlowForge Project

## What is this

This is what executes template instances composed of blocks connected by lines.  
This is what starts procedure instances for execution.  
Ideally, there are no internal project dependencies.

```java

// Procedure 1:
// (input data) -> [block-1: performs stage 1] --> [block-2: performs some stage] --> [block-N: performs stage N]
// At the code level, each block receives text and returns text: block input data and block execution results.
// Text transformation is the task of application code, if it is needed at all. (For example, input is json, output is json, or input is a prompt as plain text and output is also plain text or text with links.)

// Pseudocode.

// Create blocks.
List<Block> blocks = new ArrayList<>(Arrays.asList(
    new Block("block-1", ...),
    new Block("block-2", ...),
    new Block("block-3", ...)
));

// Create lines.
List<Line> lines = new ArrayList<>(Arrays.asList(
    new Line("block-1", "block-2", ...),
    new Line("block-2", "block-3", ...)
));

// Create a procedure instance.
// Inside the instance, mutual resolution of block and line connections is performed.
Instance instance = new Instance("Calculate some", blocks, lines);

// Add instance to execution queue.
Parameters parameters = new Parameters(
    new ArrayList(Arrays.asList(
        new Parameter("block-1", "<block input data as text...>"),
        ...
    ))
);
// instanceExecutor - something that executes block chains.
// Some service, an available instance of some class that can
// execute procedures, including using multithreading.
instanceExecutor.add(instance, parameters, ...);

// For actual execution of instances, something in the code must
// call the main execution method:
instance.run();
// This can be a timer tick, an event, a loop pass (worst case), etc.

// Execution results are almost always _better_ obtained on request,
// with configurable frequency and in required volumes.
String results = instanceExecutor.getResults(instance.getName(), ...);

// Results (results) are simpler and more universal to obtain also as text.
// This can be json, for example, containing bindings like
//      block-name:block-output-data.

// You can also pause an instance, stop an instance, or even
// interrupt immediately. The result of these actions depends on the implementation
// of each specific block.
```

flow-forge-engine is part of the FlowForge project, ideally not connected to other parts of the project.  
That is, if desired, you can use only this part in the code to run your own procedures.

## Quick Start

## Licensing

This module is part of the FlowForge project and follows the same licensing terms.

## Author

Konstantin Terskikh  
Email: <kostus.online.1974@yandex.ru>, <kostus.online@gmail.com>  
Saint Petersburg, 2025


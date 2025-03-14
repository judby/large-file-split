# Large File Splitter
Being able to split very large files into smaller to support uploads and similar.

Created mainly to support amazon S3 multipart file uploads, but implementation does not
depend on any Amazon services.

Using the latest and greatest from JAVA 24.

## JAVA technologies

### Memory mapped files
We do not want to load huge files into java heap memory so will be using memory mapped files.

Using the Foreign Function & Memory API we map the file into a shared arena for processing.

### Virtual threads
Since reading from files and writing to other media (files, sockets) are potentially blocking 
operations, these are great candidates for virtual threads.

The default split mechanism uses a virtual thread per part, potentially creating
hundredths of virtual threads.

### Structured concurrency

Any file part operation that fails must terminate the entire proecss so using Structured
Concurrency is ideal.

### HttpClient

...

## Using

...

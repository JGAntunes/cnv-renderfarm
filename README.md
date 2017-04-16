# cnv-renderfarm

If your system has docker:

* Run `./build.sh` to build the application
* Run `RUN=1 ./build.sh` to build and start the application.

Or if you prefer using java and ant:

* Run `ant` to generate a .jar file with the application
* Run `ant run` to start the application

Test request:
`curl -v "http://localhost:8000/r.html?f=test01.txt&sc=3&sr=3&wc=3&wr=3&coff=3&roff=3"`

## License
All the code that's **not under** the lib folder is under MIT license.


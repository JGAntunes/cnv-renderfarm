CNV Report
===

Made with love by,
  Joao Antunes
  Joao Tiago

**Request Cost Estimation**
To estimate the cost of the request we first search a cache for a request 
containing the same parameters. In case of miss we try to estimate the cost based
on cost function. This cost estimation takes in to account the window size (wcols * wrows).
Through this estimation we just want to know the order of magnitude of the request.

**TODO**: add equation here

**Auto-Scaling**
Through requests to the cloudWatch API the auto-scaler monitors the instance pool
total CPU average state. Getting 2min data windows every 5min.
For every iteration of the scaler, in the case of the total avg. CPU being above
0.6 (60%) a new instance is created. The value 0.6 was choosen because since new 
metrics from cloudwatch arive at a low frequency we have to be able to react 
beforehand, otherwise we risk reaching a congested system state.
In the case of the average being bellow 0.3 (30%) CPU utilization the instance with 
the least amount of CPUBalance is flagged for removal, preventing new requests from
being scheduled and terminating it when the current ones are over.

**Data-Structures**
In order to keep track of the state of the machines four pools were created.
A pool for healthy instances, unhealthy instances, flagged instances and terminating
instances.
The first pool corresponds to the group of instances that are (ready for) answering
requests. The second **TODO** FINISH ME!.
With the intent of store the requests execution time, and keeping them accessible
we use a pseudo-requestCache (Java Native DataStructure used as a cache).

**Fault-Tolerance/Prevention/Recovery**
If an instance (worker) fails to responds to a request, either by through a timeout
500 status-code, etc, we reschedule the request to another worker.
The whole process is transparent to the client, changing only the varying only the
time the request takes to answer.
In order to prevent sending requests to cripled instances, we regulary run an health
check against the machines. It consists of an HTTP request to the path /healthcheck.
This healthcheck has a timeout.

**Task-Scheduling Algorithm**


CNV Report
===

Made with love by,
  Joao Antunes
  Joao Tiago

**Request Cost Estimation**  
To estimate the cost of the request we first search a cache for a request 
containing the same parameters. In case of miss we try to estimate the cost based
on cost function. We recognized that the cost of a request depends mainly on the
size of the window to render. 
Thus the cost estimation takes in to account mainlythe window size (wcols * wrows).
The formula used to calculate the time was obtain through means of a linear 
regression. The obtained slope of the straight was 0.000008.  
Ex: (4000x3000 window) 12.000.000 window size * 0.000008 = 96sec estimate

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
One flaw of the scheduler resides in the fact that it was built to add and remove
one instance at a time. Because of this it won't be able to react quicly enough in
big systems.

**Data-Structures**  
In order to keep track of the state of the machines five pools were created.
A pool for healthy instances, unhealthy instances, flagged instances and terminating
instances, gracePeriod instances.   
For each instance we keep a queue of all the current requests.
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
When a request is recieved, in order to schedule it to a machine we first estimate 
its cost throught the function described above. Then for each machine we run the 
following formula:   
`SUM(cost of all scheduled req + the cost of the new one)/Free CPU = Weight`.   
We choose to schedule to the one instance that achieves the lower weight.  
This algorithm fails to get a granular sence of what is happening to each req.
The cost associated to the scheduled requests is the flat estimate we did before
assigning it to that same machine. Unfortunately, for the algorithm it is the same
if the request has 1sec of elapsed time or 1min. These values could have been
"normalized" before the sum by calculating the estimated time remaining on the
request.
The scheduling algorithm also fails to get a sence of the available credits of the
instance.

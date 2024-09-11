# security-augmentor-blocking-issue

Simple project to demonstrate how concurrent requests are limited to the event loop thread number if a `SecurityIdentityAugmentor` performs a blocking operation.

`FooSecurityAugmentor` doesn't do anything for unauthenticated requests, and it performs an enrichment for authenicated users, running it in a worker thread (`authenticationRequestContext.runBlocking`). Sometimes this enrichment operation may take longer than usual and, although it's running in a worker thread, we've noticed that if there are N requests in progress (with N = number of event loop threads) the application can't serve any other _authenticated_ request (while _unauthenticated_ ones work as expected). 

For example, assume we have 2 event loop threads. We perform the following request:

```
time curl -u user1:pwd1 localhost:8080/hello
Hello from Quarkus RESTcurl -u user1:pwd1 localhost:8080/hello  0.01s user 0.01s system 0% cpu 20.198 total
```

The enrichment operation is emulated using a `Thread.sleep` that waits 20s, then it returns. Result is cached, so at this point performing another request for `user1` doesn't trigger the enrichment process and it returns immediately.

```
time curl -u user1:pwd1 localhost:8080/hello
Hello from Quarkus RESTcurl -u user1:pwd1 localhost:8080/hello  0.01s user 0.01s system 54% cpu 0.041 total
```

At this point, let's perform 2 requests simultaneously for other two users:

```
seq 2 3 | xargs -P 2 -I % bash -c 'INDEX=%;time curl -u user${INDEX}:pwd${INDEX} http://localhost:8080/hello'
Hello from Quarkus RESTHello from Quarkus REST

real	0m20.015s
user	0m0.002s
real	0m20.014s
sys	0m0.006s
user	0m0.003s
sys	0m0.006s
```

These requests will hang for 20s; now, if we run an _unauthenticated_ request it will return immediately:

```
time curl localhost:8080/hello         
Hello from Quarkus RESTcurl localhost:8080/hello  0.01s user 0.00s system 60% cpu 0.010 total
```

But if we run 

```
time curl -u user1:pwd1 localhost:8080/hello
Hello from Quarkus RESTcurl -u user1:pwd1 localhost:8080/hello  0.01s user 0.01s system 0% cpu 18.280 total
```

although it should return immediately because the result is cached, it will wait until one of the two previous requests end. 

The same happens if we increase the number of event loop threads: for example, with 4 threads, we can reproduce the same issue by running

```
seq 2 5 | xargs -P 4 -I % bash -c 'INDEX=%;curl -u user${INDEX}:pwd${INDEX} http://localhost:8080/hello'
```

Since the enrichment process is run in a worker thread, I believe the number of concurrent requests should be limited by the number of worker threads and not the number of event loop threads.

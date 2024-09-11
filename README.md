# security-augmentor-blocking-issue

Simple project to demonstrate how concurrent requests are limited to the event loop thread number
if a `SecurityIdentityAugmentor` performs a blocking operation.

`FooSecurityAugmentor` doesn't do anything for unauthenticated requests, and it performs an
enrichment for authenticated users, running it in a worker thread (`authenticationRequestContext.runBlocking`).
Sometimes this enrichment operation may take longer than usual and, although it's running in a
worker thread, we've noticed that if there are N requests in progress (with N = number of event
loop threads) the application can't serve any other _authenticated_ request (while
_unauthenticated_ ones work as expected).
Since the enrichment process is run in a worker thread, I think the number of concurrent requests
should be limited by the number of worker threads and not the number of event loop threads.

## Use case 1 (without cache)

Suppose `user1`, `user2`, `user3` perform a request at the same time:

```
seq 1 3 | xargs -P 3 -I % bash -c 'INDEX=%;time curl -u user${INDEX}:pwd${INDEX} http://localhost:8080/hello'
Hello from Quarkus RESTHello from Quarkus REST
real	0m20.160s
user	0m0.005s
sys	0m0.003s

real	0m20.161s
user	0m0.003s
sys	0m0.005s
Hello from Quarkus REST
real	0m40.146s
user	0m0.004s
sys	0m0.007s
```

The enrichment process is simulated using a `Thread.sleep` that waits for 20s, so I'd expect all
the three users to wait 20s; however, two users (depending on which requests are accepted first)
wait for 20s, while one waits for ~40s because the request is not processed until the first two ends. 

While the three request above are being served, unauthenticated requests are served immediately
(which is expected, since the event loop thread shouldn't be busy while the blocking tasks run):

```
time curl localhost:8080/hello
Hello from Quarkus RESTcurl localhost:8080/hello  0.01s user 0.01s system 73% cpu 0.032 total
```

The same happens if we increase the number of event loop threads: for example, with 4 threads, we can reproduce the same issue by running

```
seq 1 5 | xargs -P 5 -I % bash -c 'INDEX=%;time curl -u user${INDEX}:pwd${INDEX} http://localhost:8080/hello'
Hello from Quarkus RESTHello from Quarkus RESTHello from Quarkus RESTHello from Quarkus REST
real	0m20.193s
user	0m0.006s
sys	0m0.006s

real	0m20.193s
user	0m0.007s
sys	0m0.004s

real	0m20.195s
user	0m0.007s
sys	0m0.006s

real	0m20.194s
user	0m0.004s
sys	0m0.007s
Hello from Quarkus REST
real	0m40.153s
user	0m0.010s
sys	0m0.003s
```

Four requests will take 20s, the fifth will take 40s.

## Use case 2 (with cache)

Since authentication enrichment may take long, we're caching the results. Again, we assume to have 2 event loop threads. We perform this request:

```
time curl -u user1:pwd1 localhost:8080/hello
Hello from Quarkus RESTcurl -u user1:pwd1 localhost:8080/hello  0.01s user 0.01s system 0% cpu 20.198 total
```

At this point the result is cached, performing another request for `user1` doesn't trigger the enrichment process and it returns immediately:

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

These requests will hang for 20s; again, if we run an _unauthenticated_ request it will return immediately:

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


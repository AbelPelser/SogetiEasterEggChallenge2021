## Description

Easter is magnificent - eating chocolate, finding Easter eggs, eating chocolate, celebrating spring and eating chocolate. 
How can one possibly improve upon this? As any (well, some) manager will tell you, the answer is obvious. 
Automation, containerization, one-way functions and - crucially - **Blockchain**. My Easter Egg submission does all of this - and more!


What is better than one Docker container? Yes, chocolate. But also: two Docker containers! And what is better than both? A Docker container *in* a Docker container!
That's why this Easter Egg Generation Creation hosts a whopping *eleven* Docker containers in one giant übercontainer - of sorts.
(It definitely, *definitely* has nothing to do with the fact that I couldn't sign up for AWS with my credit card.)

Together, these eleven containers host a Hyperledger Fabric network running a Chaincode (Smart Contract) which generates your Easter eggs.
These eggs also serve as a fingerprint - their patterns are based on your name, but cannot be traced back to it.

The provided client code connects to this network, kindly asks for an egg based on your name - and only after all network peers agree
to this idea will they begrudgingly let you have your treat.


## Instructions
(I tried making this easier with a Dockerfile, proper entry points and so on, but decided to quit after midnight.)

`docker pull apelser/eggchallenge:2.0`

`docker network create --subnet=172.10.10.0/16 bcnet`

`docker run --net bcnet --ip 172.10.10.2 --privileged -d --name abelssubmission apelser/eggchallenge:2.0`

`docker exec -it abelssubmission sh`

And in the container

`cd /fabric && ./download_images.sh && ./pipeline.sh`

Then, on the host, in the directory `run`:
`./compile_client.sh && ./run.sh`

# Mini Bitcoin - Simple cryptocurrency
> Minimal Bitcoin Protocol in Java (Satoshi White Paper, not Bitcoin Core)

## Description
Just small 7 files. Standard Java Library. No JARs.

Our goal is to decentralize the power of creating currencies. 

There are about 10 million of Java programmers around the world.

Currencies must compete in the market, like any goods or services. 

Fork this. Create your own cryptocurrency!

[Bitcoin White Paper](https://bitcoin.org/bitcoin.pdf)

## How to GO
* Install Java Runtime Environment

* Download [Eclipse IDE](https://www.eclipse.org/downloads/)

* Clone or fork this repository.

* Eclipse File->New->Java Project (copy files inside when ready)

## Understanding the code
Inside mbtc.Main you get the "main method" starting point.

Put a breakpoint in the first line 

```
* U.logVerbosityLevel = 2;
```

Go debugging. :-)

No threadssss, just ONE execution line.

RPC not ready yet.

## Docker
You can make

```
$ docker build .
```
to create a docker image.
And/Or 

```
$ docker-compose up
```

To start two nodes and check how the peers comunicate


## What is address?
It's just a shortcut to the public key. Once your public key is on the blockchain, you will receive coins using small 
address, instead of use the large public key.

Example: 

```
publicKey: MFYwEAYHKoZIzj0CAQYFK4EEAAoDQgAEiCEOTeXDzM8lDlj21vmzQxzu9w6aN8f98uq3fSBwBQtL627QBvH0Rk8xsT9leiYtByp815SNPEcxS0cFXEm4IA==

address: 4d744-2
```
You can use (to send 1 mbtc):

```
/send 1 MFYwEAYHKoZIzj0CAQYFK4EEAAoDQgAEiCEOTeXDzM8lDlj21vmzQxzu9w6aN8f98uq3fSBwBQtL627QBvH0Rk8xsT9leiYtByp815SNPEcxS0cFXEm4IA==
```
Or (IF the publickey above is already in the blockchain. More than 4 billions of possible addresses)

```
/send 1 4d744-2 
```

## Dont be shine. Clone this. Fork this. Create your coin!
Money must compete in the market, like any good or service.

This is the right thing to do, not one world bitch-coin.

If you need my help, don't hesitate to ask me.

Enjoy!
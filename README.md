# Mini Bitcoin - Simple cryptocurrency
> Minimal Bitcoin Protocol in Java (Satoshi White Paper, not Bitcoin Core)

## Description
Minimal Bitcoin Protocol. Only standard java library.

Wallet(Private and Public keys), Blockchain, Miner, P2P (Server and client), RPC(http).. all ready.

[Bitcoin / *Mini-Bitcoin White Paper](https://bitcoin.org/bitcoin.pdf) (*except Merkle-tree)

## Requirements

* Git
* Docker

## How To

For the first time, do

```
git clone https://github.com/soldate/mini-bitcoin.git
```

Then

```
cd mini-bitcoin/
git fetch origin
git reset --hard origin/master
docker build -t mbtc .
docker run -it -p 10762:10762 -p 8080:8080 -v "${PWD}/data:/mini-bitcoin/data" --rm mbtc
```

Go to [http://localhost:8080](http://localhost:8080)

That's it! :-D

To stop, try

```
docker ps
docker stop CONTAINER_ID
```


## What is address?

It's just a shortcut to the public key. Once your public key is on the blockchain, you will receive coins using small 
address, instead of use the large public key.

Example: 

```
publicKey: MFYwEAYHKoZIzj0CAQYFK4EEAAoDQgAEiCEOTeXDzM8lDlj21vmzQxzu9w6aN8f98uq3fSBwBQtL627QBvH0Rk8xsT9leiYtByp815SNPEcxS0cFXEm4IA==

address: A1B2C3
```
You can use (to send 1 mbtc):

```
/send 1 A1B2C3 
```

Or (IF the publickey is not in the blockchain yet.)

```
/send 1 MFYwEAYHKoZIzj0CAQYFK4EEAAoDQgAEiCEOTeXDzM8lDlj21vmzQxzu9w6aN8f98uq3fSBwBQtL627QBvH0Rk8xsT9leiYtByp815SNPEcxS0cFXEm4IA==
```

More than 4 billions of possible addresses.

## Help

If you need my help, don't hesitate to ask me.

[My twitter](https://twitter.com/_oliberal)

Enjoy!
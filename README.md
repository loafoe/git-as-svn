# About project
[![Join the chat at https://gitter.im/bozaro/git-as-svn](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/bozaro/git-as-svn?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status](https://travis-ci.org/bozaro/git-as-svn.svg?branch=master)](https://travis-ci.org/bozaro/git-as-svn)
[![Download](https://img.shields.io/github/release/bozaro/git-as-svn.svg)](https://github.com/bozaro/git-as-svn/releases/latest)

## Documentation links
You can read documentation by links:

 * English:
   [HTML multipage](https://bozaro.github.io/git-as-svn/html/en_US/),
   [HTML single page](https://bozaro.github.io/git-as-svn/htmlsingle/en_US/),
   [PDF](https://bozaro.github.io/git-as-svn/pdf/git-as-svn.en_US.pdf),
   [EPUB](https://bozaro.github.io/git-as-svn/epub/git-as-svn.en_US.epub)
 * Russian:
   [HTML multipage](https://bozaro.github.io/git-as-svn/html/ru_RU/),
   [HTML single page](https://bozaro.github.io/git-as-svn/htmlsingle/ru_RU/),
   [PDF](https://bozaro.github.io/git-as-svn/pdf/git-as-svn.ru_RU.pdf),
   [EPUB](https://bozaro.github.io/git-as-svn/epub/git-as-svn.ru_RU.epub)

## What is it?
This project is an implementation of the Subversion server (svn protocol) for git repository.

It allows you to work with a git repository using the console svn, TortoiseSVN, SvnKit and similar tools.

## Why do we need it?
This project was born out of division teams working on another project into two camps:

 * People who have tasted the Git and do not want to use Subversion (eg programmers); 
 * People who do not get from Git practical use and do not want to work with him, but love Subversion (eg designers).

To divide the project into two repository desire was not for various reasons.

At this point, saw the project (http://git.q42.co.uk/git_svn_server.git with Proof-of-concept implementation svn server
for git repository. After this realization svn server on top of git and didn't seem completely crazy idea (now it's
just a crazy idea) and started this project.

## Project status
Implementation status:

 * git submodules - partial
   * git submodules transparently mapped to svn
   * git submodules modification with svn not supported
 * git-lfs
 * svn properties - partial
   * some files one-way mapped to svn properties (example: .gitignore)
   * custom properties not supported
   * the commit requires that the properties of the commited file / directory exactly match the data in the repository
 * svn checkout, update, switch, diff - works
 * svn commit - works
 * svn copy, svn move - allowed copy and move commands, but copy information lost in repository
 * svn cat, ls - works
 * svn replay (svnsync) - works

Also supported:

 * Embedded git-lfs server for Git users.
 * GitLab integration.

## System requirements
Server-side:

 * Java 8+
 * git repository

On the client side it is strongly recommended to use the tool with support for Subversion 1.8+.

# How to use

## Install on Ubuntu/Debian

You can install Git as Subversion by commands:
```bash
# Add package source
echo "deb https://dist.bozaro.ru/ debian/" | sudo tee /etc/apt/sources.list.d/dist.bozaro.ru.list
curl -s https://dist.bozaro.ru/signature.gpg | sudo apt-key add -
# Install package
sudo apt-get update
sudo apt-get install git-as-svn
```

## Run from binaries

For quick run you need:

 * Install Java 1.8 or later
 * Download binaries archive from: https://github.com/bozaro/git-as-svn/releases/latest
 * After unpacking archive you can run server executing:<br/>
   `bin/git-as-svn --config doc/config.example --show-config`
 * Test connection:<br/>
   `svn ls svn://localhost/example`<br/>
   with login/password: test/test

As result:

 * Server creates bare repository with example commit in directory: `example.git`
 * The server will be available on svn://localhost/example/ url (login/password: test/test)

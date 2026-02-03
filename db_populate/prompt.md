Write a script that reads arbitrary number of paths to zip files.
For each zip file it should check that it is indeed a zip file, then extract from the zipa a file called "NCC.HTML"
From the NCC.HTMl the meta linse with the creator and the title, fore example
```
	<meta name="dc:creator" content="Patricia Cornwell"/>
	<meta name="dc:title" content="Predator: osamnaesti slucaj inspecktprice"/>

```
should be extracted, and paresed for the content, for example

```
Patricia Cornwell Bloom
Predator: osamnaesti slucaj	
```
The name of the author (creator) should be reduced to  the maximum of 2 tokens, and the title to 3 tokens and max 15 chraracters,
after stripping the punctuation marks
dropping the tokens which would be truncated by the 15 character rule
for example
```
Patricia Cornwell
Predator
```
A new directory should be created in the same directory the zip file is in, with the name being put together 
by concatentaing the lowercased title and creator tokens wiht dash, and then connecting these thwo strings by underscode, like
```
predator_patricia-cornwell
```
The zip file should be moved to that directory and unzipped.



In the case of any failure a warnning should be issued, and the processing should move to the next zipped file

Proved a dry run options in which no directories are created and nothing is move or unomcpressed, but the script only prints statments explainign what will be created and what eill be moved and unzipeed in the actual productions run

The script should havve main() and shebang line, uses arg parsing and type hinting, docstrings and line comments as needed.



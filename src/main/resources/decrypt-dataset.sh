#!/bin/bash


function decryptFile()
{
	if [ -e $1 ] && [ -e $2 ]
	then
		echo "Decrypting '$1'..."
		echo "keyfile: '$2'"
	else
		echo "Cannot find '$1' or '$2'. Skipping."
		exit
	fi

	openssl base64 -d -in $2 -out $2.keybin
	openssl enc -aes-256-cbc -pbkdf2 -d -in $1 -out $1.recovered -kfile $2.keybin
	shred -u $2.keybin
	# mv $1.recovered $1
	echo "decrypted file with success"
}

decryptFile $1 $2
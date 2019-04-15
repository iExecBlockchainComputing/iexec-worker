#!/bin/bash


function decryptFile()
{
	ENC_DATA_FILE="$1"
	DEC_DATA_FILE="$1.recovered"
	SECRET_FILE="$2"
	KEY_FILE="$2.keybin"

	if [ -e ${ENC_DATA_FILE} ] && [ -e ${SECRET_FILE} ]
	then
		echo "[SHELL] encrypted data file '${ENC_DATA_FILE}'"
		echo "[SHELL] secret file: '${SECRET_FILE}'"
	else
		echo "[SHELL] cannot find '${ENC_DATA_FILE}' or '${SECRET_FILE}'. Skipping."
		exit
	fi

	openssl base64 -d -in ${SECRET_FILE} -out ${KEY_FILE} 2>&1
	echo "[SHELL] converted secret file from base64 to bin format"

	openssl enc -d -aes-256-cbc -in ${ENC_DATA_FILE} -out ${DEC_DATA_FILE} -kfile ${KEY_FILE} 2>&1
	echo "[SHELL] decrypted dataset file"

	shred -u ${KEY_FILE} 2>&1
	rm -f ${KEY_FILE} 2>&1
	rm -f ${SECRET_FILE} 2>&1

	echo "[SHELL] ended with success"
}

decryptFile $1 $2
/*******************************************************************************
 * Copyright (c) 2012, Institute for Pervasive Computing, ETH Zurich.
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the Institute nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE INSTITUTE AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE INSTITUTE OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * 
 * This file is part of the Californium (Cf) CoAP framework.
 ******************************************************************************/
package ch.ethz.inf.vs.californium.dtls;

import java.io.ByteArrayInputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import ch.ethz.inf.vs.californium.util.DatagramReader;
import ch.ethz.inf.vs.californium.util.DatagramWriter;

/**
 * The server MUST send a Certificate message whenever the agreed-upon key
 * exchange method uses certificates for authentication. This message will
 * always immediately follow the {@link ServerHello} message. For details see <a
 * href="http://tools.ietf.org/html/rfc5246#section-7.4.2">RFC 5246</a>.
 * 
 * @author Stefan Jucker
 * 
 */
public class CertificateMessage extends HandshakeMessage {

	// Logging ///////////////////////////////////////////////////////////

	private static final Logger LOG = Logger.getLogger(CertificateMessage.class.getName());

	// DTLS-specific constants ///////////////////////////////////////////
	
	/**
	 * <a href="http://tools.ietf.org/html/rfc5246#section-7.4.2">RFC 5246</a>:
	 * <code>opaque ASN.1Cert<1..2^24-1>;</code>
	 */
	private static final int CERTIFICATE_LENGTH_BITS = 24;

	/**
	 * <a href="http://tools.ietf.org/html/rfc5246#section-7.4.2">RFC 5246</a>:
	 * <code>ASN.1Cert certificate_list<0..2^24-1>;</code>
	 */
	private static final int CERTIFICATE_LIST_LENGTH = 24;

	// Members ///////////////////////////////////////////////////////////

	/**
	 * This is a sequence (chain) of certificates. The sender's certificate MUST
	 * come first in the list.
	 */
	private X509Certificate[] certificateChain;

	/** The encoded chain of certificates */
	private List<byte[]> encodedChain;

	/** The total length of the {@link CertificateMessage}. */
	private int messageLength;

	// Constructor ////////////////////////////////////////////////////

	public CertificateMessage(X509Certificate[] certificates) {
		this.certificateChain = certificates;
	}

	// Methods ////////////////////////////////////////////////////////

	@Override
	public HandshakeType getMessageType() {
		return HandshakeType.CERTIFICATE;
	}

	@Override
	public int getMessageLength() {
		// the certificate chain length uses 3 bytes
		// each certificate's length in the chain also uses 3 bytes
		if (encodedChain == null) {
			messageLength = 3;
			encodedChain = new ArrayList<byte[]>(certificateChain.length);
			for (X509Certificate cert : certificateChain) {
				try {
					byte[] encoded = cert.getEncoded();
					encodedChain.add(encoded);

					// the length of the encoded certificate plus 3 bytes for
					// the length
					messageLength += encoded.length + 3;
				} catch (CertificateEncodingException e) {
					encodedChain = null;
					LOG.severe("Could not encode the certificate.");
					e.printStackTrace();
				}
			}
		}
		return messageLength;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(super.toString());
		sb.append("\t\tCertificates Length: " + (getMessageLength() - 3) + "\n");
		int index = 0;
		for (X509Certificate cert : certificateChain) {
			sb.append("\t\t\tCertificate Length: " + encodedChain.get(index).length + "\n");
			sb.append("\t\t\tCertificate: " + cert.toString() + "\n");

			index++;
		}

		return sb.toString();
	}

	public X509Certificate[] getCertificateChain() {
		return certificateChain;
	}

	// Serialization //////////////////////////////////////////////////

	@Override
	public byte[] toByteArray() {
		DatagramWriter writer = new DatagramWriter();
		writer.writeBytes(super.toByteArray());

		// the size of the certificate chain
		writer.write(getMessageLength() - 3, CERTIFICATE_LIST_LENGTH);
		for (byte[] encoded : encodedChain) {
			// the size of the current certificate
			writer.write(encoded.length, CERTIFICATE_LENGTH_BITS);
			// the encoded current certificate
			writer.writeBytes(encoded);
		}

		return writer.toByteArray();
	}

	public static HandshakeMessage fromByteArray(byte[] byteArray) {
		DatagramReader reader = new DatagramReader(byteArray);

		int certificateChainLength = reader.read(CERTIFICATE_LENGTH_BITS);

		List<Certificate> certs = new ArrayList<Certificate>();

		CertificateFactory certificateFactory = null;
		while (certificateChainLength > 0) {
			int certificateLength = reader.read(CERTIFICATE_LENGTH_BITS);
			byte[] certificate = reader.readBytes(certificateLength);

			// the size of the length and the actual length of the encoded
			// certificate
			certificateChainLength -= 3 + certificateLength;

			try {
				if (certificateFactory == null) {
					certificateFactory = CertificateFactory.getInstance("X.509");
				}
				Certificate cert = certificateFactory.generateCertificate(new ByteArrayInputStream(certificate));
				certs.add(cert);
			} catch (CertificateException e) {
				LOG.severe("Could not generate the certificate.");
				e.printStackTrace();
			}
		}

		return new CertificateMessage(certs.toArray(new X509Certificate[certs.size()]));
	}

}
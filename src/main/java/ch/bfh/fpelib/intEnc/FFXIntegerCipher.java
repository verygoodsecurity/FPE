package ch.bfh.fpelib.intEnc;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.BitSet;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.security.GeneralSecurityException;

import ch.bfh.fpelib.Key;
import ch.bfh.fpelib.messageSpace.IntegerMessageSpace;
import ch.bfh.fpelib.messageSpace.OutsideMessageSpaceException;

/**
 * This class is an implementation of the "FFX Mode of Operation for Format-Preserving Encryption": <a href="http://csrc.nist.gov/groups/ST/toolkit/BCM/documents/proposedmodes/ffx/ffx-spec.pdf">http://csrc.nist.gov/groups/ST/toolkit/BCM/documents/proposedmodes/ffx/ffx-spec.pdf</a>
 *
 * <p>FFXIntegerCipher is a Format Preserving Encryption (FPE) Cipher for numbers from zero to a maximum of 38 decimal digits (128 bits).<br>
 * The FFXIntegerCipher encrypts a given input number from a specified range in such way, that the output value is also a number from the same range.
 * This range from zero to a maximum value is defined by an IntegerMessageSpace delivered in the constructor.</p>
 * 
 * <p>Following a simple example how to use a FFXIntegerCipher. Here the aim is to encrypt the number 12345 into another number in the range of 0-1000000:</p>
 * 
 * <pre><code>IntegerMessageSpace intMS = new IntegerMessageSpace(BigInteger.valueOf(1000000));
 *		FFXIntegerCipher ffx = new FFXIntegerCipher(intMS);
 *
 *		BigInteger plaintext = BigInteger.valueOf(12345);
 *		BigInteger ciphertext = ffx.encrypt(plaintext,key,tweak); //possible result: 503752</code></pre>
 *
 * <p>The ciphertext could now be for example 503752.
 * By putting this number into the decrypt-method of the FFXIntegerCipher, with the same key and the same tweak, you will receive the plaintext, in this case 12345 back.</p>
 * 
 * <code>BigInteger decPlaintext = ffx.decrypt(ciphertext, key,tweak); //result: 12345</code>
 * 
 * <p>The key is a random 16-byte-array and has to be the same for decrypting a value as he was for encrypting it.<br>
 * The tweak is a value similar to an initialization vector (iv) or a salt on hashing in the sense that he prevents a deterministic encryption. 
 * A tweak can be arbitrary long and has to be the same for decrypting a value as he was for encrypting it.</p>
 * 
 * <p>The parameters in the FFX algorithm are set as follows:</p>
 * <ul>
 * <li>radix = 2 (number of symbols in alphabet: {0, 1})</li>
 * <li>feistel method = 2 (alternating feistel)</li>
 * <li>addition operator = 0 (characterwise addition (xor))</li>
 * </ul>
 */
public class FFXIntegerCipher extends IntegerCipher {

	private static final int MIN_BIT_LENGTH = 8;	//the minimum of ffx is 8 bit
	private static final int MAX_BIT_LENGTH = 128;	//ffx is restricted to 128 bit
	private static final byte VERS = 1; 			//version: 1
	private static final byte METHOD = 2;   		//ffx mode: 2 = alternating Feistel
	private static final byte ADDITION = 0; 		//addition operator: characterwise addition (xor)
	private static final byte RADIX = 2; 			//number of symbols in alphabet: {0, 1} = 2
	
	/**
	 * Constructs a FFXIntegerCipher with the maximum value determined in the IntegerMessageSpace.
   *
	 * @param messageSpace IntegerMessageSpace to determine the number range of the input respectively output of the encryption/decryption
	 * @throws IllegalArgumentException if the maximum value in the IntegerMessageSpace is bigger than representable with 128 bit
	 */
	public FFXIntegerCipher(IntegerMessageSpace messageSpace) {
		super(messageSpace);
		if (messageSpace.getOrder().bitLength() > MAX_BIT_LENGTH) throw new IllegalArgumentException("Message space must not be bigger than 128 bit");
		if (messageSpace.getOrder().bitLength() < MIN_BIT_LENGTH) throw new IllegalArgumentException("Message space must be bigger or equal to 8 bit");
	}
	
	/**
	 * Constructs a FFXIntegerCipher with the maximum value determined by the parameter.
   *
	 * @param maxValue Value to determine the number range of the input respectively output of the encryption/decryption
	 * @throws IllegalArgumentException if the maximum value in the IntegerMessageSpace is bigger than representable with 128 bit
	 */
	public FFXIntegerCipher(BigInteger maxValue) {
		this(new IntegerMessageSpace(maxValue));
	}
		
	/**
	 * {@inheritDoc}
	 */
	@Override
	public BigInteger encrypt(BigInteger plaintext, Key key, byte[] tweak){
		return cipher(plaintext,key, tweak, true);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public BigInteger decrypt(BigInteger ciphertext, Key key, byte[] tweak){
		return cipher(ciphertext,key, tweak, false);
	}
	
	
	/**
	 * First method called from encrypt/decrypt methods. Checks input values for invalidities and throws an Exception if an argument is not valid.<br>
	 * Encryption/Decryption takes place in a do-while-loop to be sure that the output is a value inside the given message space.<br> 
	 * If not, the encrypted/decrypted value is encrypted/decrypted once again and so on. This procedure is called "Cycle Walking".
   *
	 * @param input plaintext to be encrypted or ciphertext to be decrypted
	 * @param key randomly computed key 
	 * @param tweak arbitrary bytes to prevent deterministic encryption
	 * @param encryption true if this method is called for an encryption, false if for a decryption
	 * @return returns a ciphertext or a plaintext, depending on encryption or decryption
	 * @throws IllegalArgumentException if input is null or negative, key is not 128 bit or tweak is longer than 64 bit
	 * @throws OutsideMessageSpaceException if plaintext/ciphertext is outside the message space
	 */
	private BigInteger cipher(BigInteger input, Key key, byte[] tweak, boolean encryption)
	{
		BigInteger maxMsValue = getMessageSpace().getMaxValue(); 
		if (input==null) throw new IllegalArgumentException("Input value must not be null");
		if (input.compareTo(BigInteger.ZERO)==-1) throw new IllegalArgumentException("Input value must not be negative");
		if (input.compareTo(maxMsValue)==1) throw new OutsideMessageSpaceException(input.toString());
		if (key==null) throw new IllegalArgumentException("Key must not be null");
		if (tweak==null) throw new IllegalArgumentException("Tweak must not be null");

		try {
			do{ input = cipherFunction(input,key, tweak, encryption);
			} while (input.compareTo(maxMsValue)==1); //Cycle Walking: While new value is outside of message space, encipher again
		
		} catch (GeneralSecurityException e) {
			throw new IllegalArgumentException("A security exception occured: " + e.getMessage());
		}
				
		return input;
	}
	
	
	/**
	 * Splits the given input into two parts and iterate them in the so-called feistel rounds.<br>
	 * In this feistel rounds each part is alternately calculated xor with the result of the round function.<br>
	 * The number of rounds depends on the amount of bits needed to represent the order of the message space (less bits -> more rounds).<br>
	 * After these rounds the two parts are concatenated again and returned as the ciphertext/plaintext.
	 * 
	 * @param input plaintext to be encrypted or ciphertext to be decrypted
	 * @param key randomly computed key 
	 * @param tweak arbitrary bytes to prevent deterministic encryption
	 * @param encryption true if this method is called for an encryption, false if for a decryption
	 * @return returns a ciphertext or a plaintext, depending on encryption or decryption
	 * @throws GeneralSecurityException wrong security parameter in AES-CBC-MAC. Should not happen because we control/check all parameters.
	 */
	private BigInteger cipherFunction(BigInteger input, Key key, byte[] tweak, boolean encryption)  throws GeneralSecurityException 
	{
		int msBitLength = getMessageSpace().getOrder().bitLength();
		int middleIndex = (msBitLength+1) / 2; 
		int nrOfRounds = determineNrOfRounds(msBitLength);
		
		//Split the input in two parts a and b
		BitSet inputBitSet = bigIntegerToBitSet(input);
		BitSet b = inputBitSet.get(0, middleIndex);
		BitSet a = inputBitSet.get(middleIndex, msBitLength+1);
		BitSet temp = new BitSet();

		
		// Initialize AES 
		IvParameterSpec ivspec = new IvParameterSpec(new byte[16]); //zero initialization vector is necessary, makes the AES encryption act as an AES CBC MAC
		Cipher aesCipher = Cipher.getInstance("AES/CBC/NoPadding");
		aesCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key.getKey(16), "AES"),ivspec);
		
		//Construct the precomputable part p of the AES input (stays the same over all rounds) and encrypt it
		byte[] p = new byte[]{0,VERS,METHOD,ADDITION,RADIX, (byte)msBitLength, (byte)middleIndex, (byte)nrOfRounds, 0,0,0,0,0,0,0,(byte)tweak.length}; //total 16 bytes 
		p = aesCipher.doFinal(p);
		
		
		//Iterate the feistel rounds
		if (encryption){
			for (int i = 0; i < nrOfRounds; i++) {
				a.xor(roundFunction(aesCipher, p, msBitLength, tweak, i, b));
				temp = a;
				a = b;
				b = temp;
			}
		} else { //decryption
			for (int i = nrOfRounds-1; i >= 0; i--) {
				temp = b;
				b = a;
				a = temp;
				a.xor(roundFunction(aesCipher, p, msBitLength, tweak, i, b));
			}
		}
			
		//Concatenate a and b
		BitSet returnBitSet = b.get(0, middleIndex);
		for (int j = middleIndex; j <= msBitLength; j++) {
			returnBitSet.set(j, a.get(j-middleIndex));
		}
		
		return bitSetToBigInteger(returnBitSet); 
	}
	

	
	/**
	 * In the round function the actual encryption/decryption with an AES CBC MAC happens. The input for this MAC is composed with a lot of parameters as defined in the FFX standard.
   *
	 * @param aesCipher a cipher object initalize to be used as AES CBC MAC
	 * @param p precomputed part of the encryption
	 * @param msBitLength bitlength of the message space which means amount of bits needed to represent the order of the message space
	 * @param tweak arbitrary bytes to prevent deterministic encryption
	 * @param roundNr current round number
	 * @param b part of the value to be encrypted/decrypted
	 * @return returns a ciphertext or a plaintext, depending on encryption or decryption
	 * @throws GeneralSecurityException wrong security parameter in AES-CBC-MAC. Should not happen because we control/check all parameters.
	 */
	private BitSet roundFunction(Cipher aesCipher, byte[] p, int msBitLength, byte[] tweak, int roundNr, BitSet b) throws GeneralSecurityException
	{
		int middleIndex = (msBitLength+1) / 2; 
		
		// Construct the variable part q of the AES input (changes every round)
		// First part of q is a array of minimum 8 bytes and consists of the tweak, a zero padding and the round number in the last byte
		byte[] paddedTweak = new byte[1 + tweak.length + ((((-tweak.length-9)%16)+16)%16)]; //  (%16)+16)%16 is necessary to prevent negative modulo values
		System.arraycopy(tweak, 0, paddedTweak, 0, tweak.length);
		paddedTweak[paddedTweak.length-1] = (byte) roundNr ; 
		
		// Second part of q is the actual plaintext b which is copied into a byte array of 8 bytes
		byte[] paddedB = new byte[8]; 
		System.arraycopy(b.toByteArray(), 0, paddedB, 0, b.toByteArray().length);
		
		// Concatenate these two parts to get the AES input q which is always a multiple of 16 bytes (AES block length)
		byte[] q = concatByteArrays(paddedB,paddedTweak); 
		
		
		// Copy precomputed p in encOutput for first XOR
		byte[] encOutput = p;

		// XOR each 16 byte block in q with the previous one and encrypt it
		for (int m=0; m < q.length;m+=16){ 
			byte[] encInput = Arrays.copyOfRange(q, m, m+16);	
			encOutput = aesCipher.doFinal(xorByteArray(encInput,encOutput));
		}
				
		BitSet ciphertext = BitSet.valueOf(encOutput);	
		
		// Calculate the amounts of bits to return
		// If the message space bitlength is even, the middleIndex is always the half of it
		if((msBitLength%2)==0 || (roundNr%2)!=0){ 
			return ciphertext.get(128-middleIndex, 128) ;
		}
		else{ //If ms bitlength is uneven, on even rounds, the bigger part is in the XOR, so we return one bit more
			return ciphertext.get(128-(middleIndex-1), 128) ;
		}
	}

	
	/**
	 * Converts a given BigInteger into a BitSet.
   *
	 * @param big BigInteger to be converted
	 * @return BitSet with the same value as the input BigInteger was
	 */
	private static BitSet bigIntegerToBitSet(BigInteger big)
	{
		BitSet bitSet = new BitSet(big.bitLength());
		for (int i = 0; i <= big.bitLength(); i++) {
			bitSet.set(i, big.testBit(i));
		}
		return bitSet;
	}
	
	
	/**
	 * Converts a given BitSet into a BigInteger
   *
	 * @param bitset BitSet to be converted
	 * @return BigInteger with the same value as the input BitSet was
	 */
	private static BigInteger bitSetToBigInteger(BitSet bitset)
	{
		BigInteger big = BigInteger.ZERO;
		for (int i=0; i< bitset.length();i++){
			if (bitset.get(i)) big = big.setBit(i);
		}
		return big;
	}
	

	/**
	 * Concatenates two byte arrays. The array firstBytes gives the smallest bytes. 
	 * Example: firstBytes[0]=00001111, firstBytes[1]=11111111, furtherBytes[0]=11110000
	 * returnArray[0]=00001111, returnArray[1]=11111111, returnArray[2]=11110000
   *
	 * @param firstBytes First ByteArray to be concatenated
	 * @param furtherBytes Second ByteArray to be concatenated
	 * @return Concatenated ByteArray
	 */
	private static byte[] concatByteArrays(byte firstBytes[],byte furtherBytes[])
	{
		byte[] returnArray = new byte[firstBytes.length + furtherBytes.length];
		System.arraycopy(firstBytes, 0, returnArray, 0, firstBytes.length);
		System.arraycopy(furtherBytes, 0, returnArray, firstBytes.length, furtherBytes.length);
		return returnArray;
	}
	
	
	/**
	 * Calculates the XOR value for two given ByteArrays.
   *
	 * @param array1 First ByteArray
	 * @param array2 Second ByteArray
	 * @return a ByteArray with the XOR value
	 */
	private static byte[] xorByteArray(byte[] array1, byte[] array2)
	{
		byte[] xorArray = new byte[array1.length];
		int i = 0;
		for (byte b : array1){
			xorArray[i] = (byte) (b ^ array2[i++]);
		}
		return xorArray;
	}
	
	/**
	 * Determines the number of feistel round necessary for the encryption/decryption to ensure a high security guarantee.
	 * The number of rounds depends on the amount of bits needed to represent the order of the message space (less bits -> more rounds).
	 * 
	 * <p>The FFX standard has no mathematically proven security for message space sizes under 8 bits. For high security applications with small numbers the use of a tiny-space FPECipher will be needed.</p>
	 *
	 * @param msBitLength bitlength of the message space which means amount of bits needed to represent the order of the message space
	 * @return number of feistel rounds determined
	 */
	private static int determineNrOfRounds(int msBitLength)
	{
		if (msBitLength >= 32) {
			return 12;
		} else if (msBitLength >= 20) {
			return 18;
		} else if (msBitLength >= 14) {
			return 24;
		} else if (msBitLength >= 10) {
			return 30;
		} else if (msBitLength >= 8) {
			return 36;
		} else
			throw new RuntimeException("Bit length of message space has to be equal or greater than 8 bit.");
	}
}

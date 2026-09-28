using System;
using System.Text;

namespace DotAot.Runtime;

public static class StringPool
{
    private const byte XOR_KEY = 0x5A;
    private static readonly byte[] EncryptedPool = new byte[0];

    public static string Get(int offset, int length)
    {
        if (length == 0) return string.Empty;

        byte[] decrypted = new byte[length];
        for (int i = 0; i < length; i++)
        {
            decrypted[i] = (byte)(EncryptedPool[offset + i] ^ XOR_KEY);
        }

        return Encoding.UTF8.GetString(decrypted);
    }
}
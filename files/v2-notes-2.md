Frans Lundberg, 2017-02-07


v2-notes.md
===========

Notes on Salt Channel v2, a new version of the protocol.

The major changes are: 

1. Binson dependency removed,
2. resume feature.

The Binson dependency is removed to make implementation more
independent. Also, it means more fixed sizes/offsets which can 
be beneficiary for performance; especially on small embedded targets.

The resume feature allows zero-way overhead for repeated connections 
between a specific client-host pair. In addition to zero-way communication overhead
a resumed session handshake uses less data (down to around 100 bytes) 
and *no asymmetric crypto*.


Temporary notes
---------------

Not in spec, of course.

* Independent message parsing. 
    Each packet should be possible to parse *independently*.
    Independently of the previous communication and any state.
    The pack/unpack can thus be completely independent.

* Single-byte alignment.
    There is not special reason to have 2, or 4-byte alignment of
    fields in this protocol. Compactness is preferred.

* Notation. Use style: "M1/Header".

* Symmetric protocol, not.
    We could design a protocol that is symmetric. Both peers could
    send their EncKey. Possibly in parallel and immediately when 
    connection is established. 
    This is beautiful and efficient, but it complicates things when 
    we add the resume ticket feature.
    So, decision now is to *not* do this.

* CloseFlag.
    Is it needed? Should it be used.


Session
=======

Message order of an ordinary successful Salt Channel session:

    Session = M1 M2 M3 M4 AppMessage*

The M1, M4 messages are sent by the client and M2, M3 by the server.
So, we have a three-way handshake (M1 from client, M2+M3 from server, and 
M4 from client). When the first application message is from the client, this
message can be sent with M4 to achieve a two-way (one round-trip) 
Salt Channel overhead.

Message order of a resumed Salt Channel session:

    Session = M1 AppMessage*

When the first application message is from the client to the server
(a common case), a resumed Salt Channel will have a zero-way overhead.
The first application message can be sent together with M1.


Message details
===============

This section describes how a message is represented as an array
of bytes. The size of the messages are know by the layer above.
The term *message* is used for a whole byte array message and
*packet* is used to refer to a byte array -- either a full message
or a part of a message.

Messages are presented below with fields of specified byte size.
If the size number has a "b" suffix, the size is in bits, otherwise
it the byte size. 

Bit order: the "first" bit (Bit 0) of a byte is the least-significant bit.
Byte order: little-endian byte order is used. The first byte is the 
least significant one.

Unless otherwise stated explicitely, bits are set to 0.

The word "OPT" is used to mark a field that may or may not exist
in the packet. It does not necesserily indicate a an optional field 
in the sense that it independently can exist or not. Whether its existance
is optional, mandatory or forbidden may dependend on other fields and/or
the state of the communication so far.


Message M1
==========
    
The first message of a Salt Channel session is always the M1 message.
It is sent from the client to the server. It includes a protocol indicator, 
the client's public ephemeral encryption key and optionally the server's
public signing key. For a fast handshake, a resume ticket may be included
in the message.

Details:

    **** M1 ****

    2   ProtocolIndicator.
        Always 0x8350 (ASCII 'S2') for Salt Channel v2.

    1   Header.
        Message type and flags.

    32  ClientEncKey.
        The public ephemeral encryption key of the client.

    32  ServerSigKey, OPT.
        The server's public signing key. Used to choose what virtual 
        server to connect to in cases when there are many to choose from.

    1   TicketSize, OPT.
        The size of the following ticket encoded in one byte.
        Allowed range: 1-127.

    x   Ticket, OPT.
        A resume ticket received from the server in a previous
        session between this particular client and server.
        The bytes of the ticket MUST NOT be interprented by the client.
        The exact interprentation of the bytes is entirely up to
        the server. See separate section.


    **** M1/Header ****

    4b  MessageType.
        Four bits that encodes an integer in range 0-15.
        The integer value is 1 for this message.

    1b  ServerSigKeyIncluded.
        Set to 1 when ServerSigKey is included in the message.

    1b  TicketIncluded.
        Set to 1 when TicketSize and Ticket are included in the message.

    1b  TicketRequested.
        Set to 1 to request a new resume ticket to use in the future to 
        connect quickly with the server.

    1b  Zero.
        Bit set to 0.
    

Messages M2 and M3
==================

The M2 message is the first message sent by the server when an 
ordinary three-way handshake is performed. It is followed by 
Message M3, also sent by the server.

By two message, M2, M3, instead one, the M3 message can be encrypted
the same way as the application messages. Also, it allows the signature
computations (Signature1, Signature2) to be done in parallel. The server
MAY send the M2 message before Signature1 is computed and M3 sent. 
In cases when computation time is long compared to communication time, 
this can decrease total handshake time significantly.

Note, the server must read M1 before sending M2. M2 depends on the contents
of M1.

    **** M2 ****
    
    1   Header.
        Message type and flags.
    
    32  ServerEncKey, OPT.
        The public ephemeral encryption key of the client.
    
    
    **** M2/Header ****

    4b  MessageType.
        Four bits that encodes an integer in range 0-15.
        The integer value is 2 for this message.

    1b  ServerEncKeyIncluded.
        Set to 1 when ServerEncKey is included in the message.

    1b  ResumeSupported.
        Set to 1 if the server implementation supports resume tickets.

    1b  NoSuchServer.
        Set to 1 if ServerSigKey was included in M1 but a server with such a
        public signature key does not exist at the end-point.

    1b  BadTicket.
        Set to 1 if Ticket was included in M1 but the ticket is not valid
        for some reason (bad format, experied, already used).
    

If the NoSuchServer condition occurs, ServerEncKey MUST NOT be included in 
the message. When it happens the client and the server should consider the
session closed once M2 has been sent.


    **** M3 ****

    This message is encrypted.

    1   Header.
        Message type and flags.

    32  ServerSigKey, OPT.
        The server's public signature key. MUST NOT be included if client 
        sent it in Message M1.

    64  Signature1
        The signature of ServerEncKey+ClientEncKey concatenated.


    **** M3/Header ****

    4b  MessageType.
        Four bits that encodes an integer in range 0-15.
        The integer value is 3 for this message.

    1b  ServerSigKeyIncluded.
        Set to 1 if ServerSigKey is included in the message.

    3b  Zero.
        Bits set to 0.
    

Message M4
==========

Message M4 is sent by the client. It finishes a three-way handshake.
If can (and often should be) be sent together with a first application 
message from the client to the server.


    **** M4 ****

    This message is encrypted.

    1   Header.
        Message type and flags.

    32  ClientSigKey.
        The client's public signature key.
        
    64  Signature2.
        Signature of ClientEncKey+ServerEncKey concatenated.


    **** M4/Header ****

    4b  MessageType.
        Four bits that encodes an integer in range 0-15.
        The integer value is 4 for this message.

    4b  Zero.
        Bits set to 0.



Application messages
====================

After the handshake, application messages are sent between the client
and the server in any order.


    **** AppMessage ****

    1   Header.
        Message type and flags.
        The header includes a close bit. If MUST be set for in the last
        AppMessage sent by Client and in the last AppMessage sent by Server.

    x   AppData.
        Application layer message encrypted with crypto_box_afternm().


    **** AppMessage/Header ****

    4b  MessageType.
        Four bits that encodes an integer in range 0-15.
        The integer value is 5 for this message.

    1b  CloseFlag.
        Set to 1 for the last message sent by the peer.

    3b  Zero.
        Bits set to 0.



Encryption
==========

TODO: write about the how messages are encrypted.
How Signatures are computed.



Resume feature
==============

The resume feature is implemented mostly on the server-side.
To the client, a resume ticket is just an arbitrary array of bytes
that can be used to improve handshake performance.
The client MUST allow changes to the format of resume tickets.
However, the server SHOULD follow the specification here. The resume
ticket specification here is the one that will be audited and should
have the highest possible security.

The resume feature is OPTIONAL. Servers may not implemement it. In that
case a server MUST always set the M2/ResumeSupported bit to 0.


Idea
----

The idea with the resume tickets is to support session-resume to signicantly
reduce the overhead of the Salt Channel handshake. A resumed session uses
less communication data and a zero-way overhead. Also, the handshake of
a resumed session does not require computationally heavy asymmetric 
cryptography operations.

Salt Channel resume allows a server to support the resume feature using only
one single bit of memory for each created ticket. This allows the server to 
have all sensitive data related to this feature in memory. Also the ticket
encryption key can be stored in memory only. If it is lost due to power 
failure the only affect is that outstanding tickets will become invalid
and a full handshake will required when client connect.

The clients store one ticket per server. A client can choose whether to use
the resume feature or not. It can do this per-server if it choses to.

A unique ticket index (Ticket/Encrypted/TicketIndex) is given to every 
ticket that is issued by the server. The first such index may, for example,
be the number of microseconds since Unix Epoch (1 January 1970 UTC).
After that, each issued ticket is given an index that equals the previously
issued index plus one.

A bitmap is used to protect agains replay attacks. The bitmap stores one bit
per non-experied ticket that is issued. The bit is set to 1 when a
ticket is issued and to 0 when it is used. Thus, a ticket can only be used
once. Of course, the bitmap cannot be of infinite size. In fact, the server
implementation can use a fixed size circular bit buffer. Using one megabyte 
of memory, the server can keep track of which tickets, out of the last 
8 million issued tickets, that have been used.


Ticket details
--------------


    **** Ticket ****

    1   Header. 
        Packet type and flags.
    
    2   KeyId.
        The server can used KeyId to choose one among multiple 
        encryption keys to decrypt the encrypted part of the ticket.
        Note, server-side implementation may choose to use only one 
        ticket encryption key for all outstanding tickets.

    16  Nonce.
        Nonce to use when decrypting Ticket/Encrypted.
        The nonce MUST be unique among all tickets encrypted with
        a particular key.

    x   Encrypted.
        Encrypted ticket data.
    
    
    **** Ticket/Header ****

    4b  MessageType.
        Four bits that encodes an integer in range 0-15.
        The integer value is 6 for this message.

    4b  Zero.
        Bits set to 0.
    
    
    **** Ticket/Encrypted ****

    This is an encrypted packet.

    1   Header.
        The Ticket/Header repeated. For authentication purposes.
        The server MUST consider the ticket invalid if Ticket/Encrypted/Header
        differs from Ticket/Header.

    2   KeyIndex.
        The KeyIndex bytes repeated. For authentication purposes.
        The server MUST consider the ticket invalid if Ticket/Encrypted/KeyIndex
        differs from Ticket/KeyIndex.

    8   TicketIndex
        The ticket index of the ticket.
        A 8-byte integer in the range: 0 to 2^63-1 (inclusive).

    32  ClientSigKey.
        The client's public signature key. Used to identify the client.

    32  SessionKey.
        The symmetric encryption key to use to encrypt and decrypt messages
        of this session.
    

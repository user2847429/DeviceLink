//! Validated control-plane message model shared conceptually with Android.
//! This is not a wire codec and must not be used as a network parser yet.

use std::fmt;

pub const PROTOCOL_VERSION: u16 = 1;
pub const MAX_CONTROL_PAYLOAD_BYTES: usize = 64 * 1024;
pub const MAX_PEER_ID_BYTES: usize = 128;
pub const MAX_MESSAGE_ID_BYTES: usize = 128;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum MessageKind {
    Hello,
    Pairing,
    Capabilities,
    Command,
    Event,
    Acknowledgement,
    TransferOffer,
    TransferAccept,
    TransferChunk,
    TransferFinish,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ControlMessage {
    pub version: u16,
    pub message_id: String,
    pub sender_peer_id: String,
    pub kind: MessageKind,
    pub payload: Vec<u8>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ValidationError {
    UnsupportedVersion(u16),
    EmptyMessageId,
    MessageIdTooLong,
    EmptyPeerId,
    PeerIdTooLong,
    PayloadTooLarge,
}

impl fmt::Display for ValidationError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{self:?}")
    }
}

impl std::error::Error for ValidationError {}

impl ControlMessage {
    /// Validate structural limits before a message is handled by a capability.
    pub fn validate(&self) -> Result<(), ValidationError> {
        if self.version != PROTOCOL_VERSION {
            return Err(ValidationError::UnsupportedVersion(self.version));
        }
        if self.message_id.is_empty() {
            return Err(ValidationError::EmptyMessageId);
        }
        if self.message_id.len() > MAX_MESSAGE_ID_BYTES {
            return Err(ValidationError::MessageIdTooLong);
        }
        if self.sender_peer_id.is_empty() {
            return Err(ValidationError::EmptyPeerId);
        }
        if self.sender_peer_id.len() > MAX_PEER_ID_BYTES {
            return Err(ValidationError::PeerIdTooLong);
        }
        if self.payload.len() > MAX_CONTROL_PAYLOAD_BYTES {
            return Err(ValidationError::PayloadTooLarge);
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn valid_message() -> ControlMessage {
        ControlMessage {
            version: PROTOCOL_VERSION,
            message_id: "msg-0001".into(),
            sender_peer_id: "peer-phone".into(),
            kind: MessageKind::Hello,
            payload: vec![1, 2, 3],
        }
    }

    #[test]
    fn accepts_a_well_formed_message() {
        assert_eq!(valid_message().validate(), Ok(()));
    }

    #[test]
    fn rejects_unknown_protocol_versions() {
        let mut message = valid_message();
        message.version = PROTOCOL_VERSION + 1;
        assert_eq!(
            message.validate(),
            Err(ValidationError::UnsupportedVersion(PROTOCOL_VERSION + 1))
        );
    }

    #[test]
    fn rejects_empty_identity_fields() {
        let mut message = valid_message();
        message.sender_peer_id.clear();
        assert_eq!(message.validate(), Err(ValidationError::EmptyPeerId));
        message = valid_message();
        message.message_id.clear();
        assert_eq!(message.validate(), Err(ValidationError::EmptyMessageId));
    }

    #[test]
    fn rejects_oversized_control_payloads() {
        let mut message = valid_message();
        message.payload = vec![0; MAX_CONTROL_PAYLOAD_BYTES + 1];
        assert_eq!(message.validate(), Err(ValidationError::PayloadTooLarge));
    }

    #[test]
    fn accepts_identifier_lengths_at_the_limit() {
        let mut message = valid_message();
        message.message_id = "m".repeat(MAX_MESSAGE_ID_BYTES);
        message.sender_peer_id = "p".repeat(MAX_PEER_ID_BYTES);
        assert_eq!(message.validate(), Ok(()));
    }

    #[test]
    fn rejects_identifiers_over_the_byte_limit() {
        let mut message = valid_message();
        message.message_id = "m".repeat(MAX_MESSAGE_ID_BYTES + 1);
        assert_eq!(message.validate(), Err(ValidationError::MessageIdTooLong));

        message = valid_message();
        message.sender_peer_id = "p".repeat(MAX_PEER_ID_BYTES + 1);
        assert_eq!(message.validate(), Err(ValidationError::PeerIdTooLong));
    }

    #[test]
    fn identifier_limits_are_measured_in_utf8_bytes() {
        let mut message = valid_message();
        message.message_id = "é".repeat(MAX_MESSAGE_ID_BYTES / 2);
        assert_eq!(message.validate(), Ok(()));

        message.message_id.push('é');
        assert_eq!(message.validate(), Err(ValidationError::MessageIdTooLong));
    }
}

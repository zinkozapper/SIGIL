class StudySession < ApplicationRecord
  serialize :metadata, coder: JSON

  before_validation :calculate_duration

  validates :client_session_id, presence: true, uniqueness: true
  validates :started_at, presence: true
  validates :ended_at, presence: true
  validates :duration_seconds,
            presence: true,
            numericality: { greater_than_or_equal_to: 0 }

  validate :ended_at_must_not_be_before_started_at
  validate :paused_duration_must_be_valid

  private

  def calculate_duration
    return unless started_at.present? && ended_at.present?

    paused_millis = metadata.is_a?(Hash) ? metadata["paused_duration_millis"] : nil
    paused_millis = 0 unless paused_millis.is_a?(Integer) && paused_millis >= 0 &&
      paused_millis <= (ended_at - started_at) * 1000
    self.duration_seconds = (ended_at - started_at - paused_millis / 1000.0).round
  end

  def paused_duration_must_be_valid
    return unless metadata.is_a?(Hash) && metadata.key?("paused_duration_millis")

    paused = metadata["paused_duration_millis"]
    unless paused.is_a?(Integer) && paused >= 0 &&
           (started_at.nil? || ended_at.nil? || paused <= (ended_at - started_at) * 1000)
      errors.add(:metadata, "paused duration must be a non-negative integer within the session")
    end
  end

  def ended_at_must_not_be_before_started_at
    return unless started_at.present? && ended_at.present?

    if ended_at < started_at
      errors.add(:ended_at, "must be greater than or equal to started_at")
    end
  end
end

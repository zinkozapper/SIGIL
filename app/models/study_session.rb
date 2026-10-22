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

  private

  def calculate_duration
    return unless started_at.present? && ended_at.present?

    self.duration_seconds = (ended_at - started_at).round
  end

  def ended_at_must_not_be_before_started_at
    return unless started_at.present? && ended_at.present?

    if ended_at < started_at
      errors.add(:ended_at, "must be greater than or equal to started_at")
    end
  end
end

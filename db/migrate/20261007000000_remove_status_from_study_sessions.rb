class RemoveStatusFromStudySessions < ActiveRecord::Migration[8.1]
  def change
    remove_column :study_sessions, :status, :string
  end
end

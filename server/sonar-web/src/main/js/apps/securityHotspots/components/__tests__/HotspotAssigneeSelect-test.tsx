/*
 * SonarQube
 * Copyright (C) 2009-2020 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
import { shallow } from 'enzyme';
import * as React from 'react';
import { KeyCodes } from 'sonar-ui-common/helpers/keycodes';
import { waitAndUpdate } from 'sonar-ui-common/helpers/testUtils';
import { searchUsers } from '../../../../api/users';
import { mockUser } from '../../../../helpers/testMocks';
import HotspotAssigneeSelect from '../HotspotAssigneeSelect';

jest.mock('../../../../api/users', () => ({
  searchUsers: jest.fn().mockResolvedValue([])
}));

it('should render correctly', () => {
  expect(shallowRender()).toMatchSnapshot();
});

it('should handle keydown', () => {
  const mockEvent = (keyCode: number) => ({ preventDefault: jest.fn(), keyCode });
  const suggestedUsers = [
    mockUser({ login: '1' }) as T.UserActive,
    mockUser({ login: '2' }) as T.UserActive,
    mockUser({ login: '3' }) as T.UserActive
  ];

  const onSelect = jest.fn();
  const wrapper = shallowRender({ onSelect });

  wrapper.instance().handleKeyDown(mockEvent(KeyCodes.UpArrow) as any);
  expect(wrapper.state().highlighted).toBeUndefined();

  wrapper.setState({ suggestedUsers });

  // press down to highlight the first
  wrapper.instance().handleKeyDown(mockEvent(KeyCodes.DownArrow) as any);
  expect(wrapper.state().highlighted).toBe(suggestedUsers[0]);

  // press up to loop around to last
  wrapper.instance().handleKeyDown(mockEvent(KeyCodes.UpArrow) as any);
  expect(wrapper.state().highlighted).toBe(suggestedUsers[2]);

  // press down to loop around to first
  wrapper.instance().handleKeyDown(mockEvent(KeyCodes.DownArrow) as any);
  expect(wrapper.state().highlighted).toBe(suggestedUsers[0]);

  // press down highlight the next
  wrapper.instance().handleKeyDown(mockEvent(KeyCodes.DownArrow) as any);
  expect(wrapper.state().highlighted).toBe(suggestedUsers[1]);

  // press enter to select the highlighted user
  wrapper.instance().handleKeyDown(mockEvent(KeyCodes.Enter) as any);
  expect(onSelect).toBeCalledWith(suggestedUsers[1]);
});

it('should handle search', async () => {
  const users = [mockUser({ login: '1' }), mockUser({ login: '2' }), mockUser({ login: '3' })];
  (searchUsers as jest.Mock).mockResolvedValueOnce({ users });

  const onSelect = jest.fn();

  const wrapper = shallowRender({ onSelect });
  wrapper.instance().handleSearch('j');

  expect(searchUsers).not.toBeCalled();
  expect(wrapper.state().open).toBe(false);

  wrapper.instance().handleSearch('jo');
  expect(wrapper.state().loading).toBe(true);
  expect(searchUsers).toBeCalledWith({ q: 'jo' });

  await waitAndUpdate(wrapper);

  expect(wrapper.state().highlighted).toBe(users[0]);
  expect(wrapper.state().loading).toBe(false);
  expect(wrapper.state().open).toBe(true);
  expect(wrapper.state().suggestedUsers).toHaveLength(3);

  jest.clearAllMocks();

  await wrapper.instance().handleSearch('');
  expect(searchUsers).not.toBeCalled();
  expect(onSelect).toBeCalledWith(undefined);
});

function shallowRender(props?: Partial<HotspotAssigneeSelect['props']>) {
  return shallow<HotspotAssigneeSelect>(<HotspotAssigneeSelect onSelect={jest.fn()} {...props} />);
}
